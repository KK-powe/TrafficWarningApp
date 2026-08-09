import json
import threading
from collections import Counter
from pathlib import Path
from typing import Callable

from .settings import Settings

ProgressCallback = Callable[[int], None]


DEFAULT_CONFIG = {
    "model": {
        "device": "auto",
        "confidence": 0.25,
        "iou": 0.5,
        "image_size": 1280,
        "tracker": "bytetrack.yaml",
        "tracked_classes": [
            "pedestrian",
            "people",
            "bicycle",
            "car",
            "van",
            "truck",
            "tricycle",
            "awning-tricycle",
            "bus",
            "motor",
        ],
    },
    "tracking": {"history_length": 60, "stale_after_frames": 90},
    "rules": {
        "wrong_way": {
            "enabled": False,
            "target_classes": [
                "bicycle",
                "motor",
                "tricycle",
                "awning-tricycle",
            ],
            "expected_direction": [1.0, 0.0],
            "minimum_history": 8,
            "minimum_displacement_pixels": 25.0,
            "maximum_direction_cosine": -0.25,
            "cooldown_seconds": 3.0,
            "severity": 25,
        },
        "restricted_zone": {
            "enabled": False,
            "polygon": [[0.05, 0.55], [0.95, 0.55], [0.95, 0.95], [0.05, 0.95]],
            "prohibited_classes": ["car", "van", "bus", "truck"],
            "cooldown_seconds": 3.0,
            "severity": 20,
        },
        "red_light": {
            "enabled": False,
            "stop_line": [[0.15, 0.62], [0.85, 0.62]],
            "red_intervals_seconds": [[0.0, 10.0]],
            "target_classes": [
                "bicycle",
                "motor",
                "tricycle",
                "awning-tricycle",
                "car",
                "van",
                "bus",
                "truck",
            ],
            "minimum_movement_pixels": 8.0,
            "cooldown_seconds": 5.0,
            "severity": 40,
        },
    },
    "risk": {
        "event_window_seconds": 3.0,
        "density_target_count": 15,
        "density_max_score": 25,
        "density_event_cooldown_seconds": 5,
        "event_weights": {
            "wrong_way": 25,
            "restricted_zone": 20,
            "red_light": 40,
            "high_density": 25,
        },
        "levels": {"medium": 25, "high": 50, "critical": 75},
    },
}


class VideoAnalyzer:
    """Load YOLO once and process submitted videos sequentially."""

    def __init__(self, settings: Settings):
        self.settings = settings
        self._model = None
        self._model_lock = threading.Lock()

    def _get_model(self):
        if self._model is None:
            if not self.settings.model_path.exists():
                raise FileNotFoundError(
                    "未找到YOLO权重："
                    f"{self.settings.model_path}。请先按README放置best.pt。"
                )
            from ultralytics import YOLO

            self._model = YOLO(str(self.settings.model_path))
        return self._model

    def analyze(
        self,
        input_path: Path,
        result_dir: Path,
        task_id: str,
        created_at_ms: int,
        progress_callback: ProgressCallback,
    ) -> dict:
        import cv2

        from .uav_warning.config import load_config
        from .uav_warning.device import select_device
        from .uav_warning.drawing import draw_scene
        from .uav_warning.models import Detection, ViolationEvent
        from .uav_warning.risk import RiskScorer
        from .uav_warning.rules import RuleEngine
        from .uav_warning.tracking import TrackHistory

        config = load_config(self.settings.config_path, DEFAULT_CONFIG)
        model_config = config["model"]
        result_dir.mkdir(parents=True, exist_ok=True)
        event_images_dir = result_dir / "events"
        event_images_dir.mkdir(exist_ok=True)
        output_video = result_dir / "annotated.mp4"
        preview_path = result_dir / "preview.jpg"

        capture = cv2.VideoCapture(str(input_path))
        if not capture.isOpened():
            raise RuntimeError("无法打开上传的视频，请确认文件未损坏且编码受支持")

        fps = float(capture.get(cv2.CAP_PROP_FPS)) or 25.0
        width = int(capture.get(cv2.CAP_PROP_FRAME_WIDTH))
        height = int(capture.get(cv2.CAP_PROP_FRAME_HEIGHT))
        total_frames = max(0, int(capture.get(cv2.CAP_PROP_FRAME_COUNT)))
        if width <= 0 or height <= 0:
            capture.release()
            raise RuntimeError("无法读取视频分辨率")

        writer = cv2.VideoWriter(
            str(output_video), cv2.VideoWriter_fourcc(*"mp4v"), fps, (width, height)
        )
        if not writer.isOpened():
            capture.release()
            raise RuntimeError("无法创建标注结果视频")

        device = select_device(str(model_config["device"]))
        history = TrackHistory(**config["tracking"])
        rule_engine = RuleEngine(config["rules"])
        risk_scorer = RiskScorer(config["risk"], fps)
        unique_tracks: dict[int, str] = {}
        api_events: list[dict] = []
        risk_sum = 0.0
        maximum_risk = 0.0
        frame_index = 0
        last_annotated = None
        best_preview = None
        best_preview_score = -1.0
        last_density_event_frame = -10**9

        with self._model_lock:
            model = self._get_model()
            # A new predictor creates a fresh ByteTrack state for each uploaded video.
            model.predictor = None
            class_names = self._normalize_class_names(model.names)
            selected_class_ids = self._resolve_class_ids(
                class_names, model_config.get("tracked_classes", [])
            )

            try:
                while True:
                    success, frame = capture.read()
                    if not success:
                        break

                    result = model.track(
                        source=frame,
                        persist=True,
                        tracker=str(model_config["tracker"]),
                        device=device,
                        conf=float(model_config["confidence"]),
                        iou=float(model_config["iou"]),
                        imgsz=int(model_config["image_size"]),
                        classes=selected_class_ids,
                        verbose=False,
                    )[0]
                    detections = self._extract_detections(result, class_names, Detection)
                    for detection in detections:
                        history.add(detection, frame_index)
                        unique_tracks[detection.track_id] = detection.class_name

                    frame_events: list[ViolationEvent] = []
                    for detection in detections:
                        frame_events.extend(
                            rule_engine.evaluate(
                                detection,
                                history,
                                frame_index,
                                fps,
                                width,
                                height,
                            )
                        )

                    density_target = int(config["risk"]["density_target_count"])
                    density_cooldown = round(
                        float(config["risk"]["density_event_cooldown_seconds"]) * fps
                    )
                    if (
                        len(detections) >= density_target
                        and frame_index - last_density_event_frame >= density_cooldown
                    ):
                        frame_events.append(
                            ViolationEvent(
                                frame_index=frame_index,
                                timestamp_seconds=round(frame_index / fps, 3),
                                track_id=-1,
                                class_name="all",
                                event_type="high_density",
                                severity=25,
                                message=f"画面内同时跟踪到{len(detections)}个交通目标",
                            )
                        )
                        last_density_event_frame = frame_index

                    risk = risk_scorer.update(
                        frame_index, len(detections), frame_events
                    )
                    risk_sum += risk.score
                    maximum_risk = max(maximum_risk, risk.score)
                    annotated = draw_scene(
                        frame,
                        detections,
                        history,
                        frame_events,
                        risk,
                        config["rules"],
                    )
                    writer.write(annotated)
                    last_annotated = annotated
                    if risk.score >= best_preview_score:
                        best_preview_score = risk.score
                        best_preview = annotated.copy()

                    for event in frame_events:
                        event_id = f"{task_id}-{len(api_events) + 1:04d}"
                        image_name = f"{event_id}.jpg"
                        cv2.imwrite(str(event_images_dir / image_name), annotated)
                        api_events.append(
                            self._to_api_event(
                                event,
                                event_id,
                                created_at_ms,
                                f"events/{image_name}",
                                risk.level,
                            )
                        )

                    history.cleanup(frame_index)
                    frame_index += 1
                    if frame_index % 10 == 0:
                        if total_frames > 0:
                            progress = min(98, int(frame_index / total_frames * 100))
                        else:
                            progress = min(98, max(1, frame_index // 10))
                        progress_callback(progress)
            finally:
                capture.release()
                writer.release()

        if frame_index == 0 or last_annotated is None or best_preview is None:
            raise RuntimeError("视频中没有可处理的画面")
        cv2.imwrite(str(preview_path), best_preview)

        class_counts = Counter(unique_tracks.values())
        analysis = {
            "taskId": task_id,
            "createdAt": created_at_ms,
            "processedFrames": frame_index,
            "durationSeconds": round(frame_index / fps, 3),
            "device": device,
            "model": self.settings.model_path.name,
            "imageSize": int(model_config["image_size"]),
            "maxRiskScore": round(maximum_risk, 2),
            "averageRiskScore": round(risk_sum / frame_index, 2),
            "classCounts": dict(sorted(class_counts.items())),
            "stats": {
                "totalWarnings": len(api_events),
                "totalTracked": len(unique_tracks),
                "highRiskCount": sum(
                    1 for event in api_events if event["riskLevel"] == 3
                ),
                "riskLevel": self._risk_level(maximum_risk),
            },
            "events": api_events,
            "annotatedImageFile": "preview.jpg",
            "resultVideoFile": "annotated.mp4",
        }
        with (result_dir / "analysis.json").open("w", encoding="utf-8") as file:
            json.dump(analysis, file, ensure_ascii=False, indent=2)
        progress_callback(100)
        return analysis

    @staticmethod
    def _normalize_class_names(names) -> dict[int, str]:
        if isinstance(names, dict):
            return {int(index): str(name) for index, name in names.items()}
        return {index: str(name) for index, name in enumerate(names)}

    @staticmethod
    def _resolve_class_ids(names: dict[int, str], requested_names: list) -> list[int] | None:
        if not requested_names:
            return None
        requested = set(requested_names)
        selected = [class_id for class_id, name in names.items() if name in requested]
        return selected or None

    @staticmethod
    def _extract_detections(result, names: dict[int, str], detection_class) -> list:
        boxes = result.boxes
        if boxes is None or boxes.id is None or len(boxes) == 0:
            return []
        detections = []
        for coordinates, class_id, confidence, track_id in zip(
            boxes.xyxy.cpu().tolist(),
            boxes.cls.int().cpu().tolist(),
            boxes.conf.cpu().tolist(),
            boxes.id.int().cpu().tolist(),
        ):
            x1, y1, x2, y2 = (round(value) for value in coordinates)
            detections.append(
                detection_class(
                    track_id=int(track_id),
                    class_id=int(class_id),
                    class_name=names.get(int(class_id), str(class_id)),
                    confidence=float(confidence),
                    box=(x1, y1, x2, y2),
                    center=((x1 + x2) // 2, (y1 + y2) // 2),
                )
            )
        return detections

    @staticmethod
    def _risk_level(score: float) -> int:
        if score >= 50:
            return 3
        if score >= 25:
            return 2
        return 1

    @classmethod
    def _to_api_event(
        cls,
        event,
        event_id: str,
        created_at_ms: int,
        image_file: str,
        frame_risk_level: str,
    ) -> dict:
        labels = {
            "wrong_way": "疑似逆行",
            "restricted_zone": "疑似进入限制区域",
            "red_light": "疑似闯红灯",
            "high_density": "交通目标密度较高",
        }
        event_risk_level = (
            3 if event.severity >= 40 else 2 if event.severity >= 20 else 1
        )
        frame_level = (
            3
            if frame_risk_level in {"HIGH", "CRITICAL"}
            else 2 if frame_risk_level == "MEDIUM" else 1
        )
        return {
            "id": event_id,
            "type": labels.get(event.event_type, event.event_type),
            "riskLevel": max(event_risk_level, frame_level),
            "timestamp": created_at_ms + round(event.timestamp_seconds * 1000),
            "frameImageFile": image_file,
            "description": event.message,
            "targetId": str(event.track_id) if event.track_id >= 0 else "-",
            "targetClass": event.class_name,
            "location": "上传视频画面",
            "reviewStatus": 0,
        }
