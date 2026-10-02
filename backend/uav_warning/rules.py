import math

from .geometry import (
    cosine_similarity,
    displacement,
    normalized_points_to_pixels,
    point_in_polygon,
    point_to_polygon_distance,
    point_to_segment_distance,
    signed_line_side,
    vector_length,
)
from .models import Detection, ViolationEvent
from .tracking import TrackHistory


class RuleEngine:
    def __init__(self, rules_config: dict):
        self.config = rules_config
        self._last_emitted_frame: dict[tuple[str, int], int] = {}
        self._operation_started_frame: dict[int, int] = {}
        self._operation_episode_emitted: set[int] = set()
        self._operation_observations: dict[int, int] = {}

    def evaluate(
        self,
        detection: Detection,
        history: TrackHistory,
        frame_index: int,
        fps: float,
        frame_width: int,
        frame_height: int,
        all_detections: list[Detection] | None = None,
    ) -> list[ViolationEvent]:
        candidates: list[tuple[str, int, str, float, int, tuple[str, ...]]] = []

        wrong_way = self.config["wrong_way"]
        if (
            wrong_way["enabled"]
            and detection.class_name in wrong_way["target_classes"]
            and self._is_wrong_way(detection, history, wrong_way)
        ):
            candidates.append(
                (
                    "wrong_way",
                    int(wrong_way["severity"]),
                    "目标运动方向与标定方向相反",
                    float(wrong_way["cooldown_seconds"]),
                    3,
                    ("目标运动方向与标定方向相反",),
                )
            )

        restricted = self.config["restricted_zone"]
        if restricted["enabled"] and detection.class_name in restricted[
            "prohibited_classes"
        ]:
            polygon = normalized_points_to_pixels(
                restricted["polygon"], frame_width, frame_height
            )
            if point_in_polygon(detection.center, polygon):
                candidates.append(
                    (
                        "restricted_zone",
                        int(restricted["severity"]),
                        "目标进入了标定的限制区域",
                        float(restricted["cooldown_seconds"]),
                        3,
                        ("目标进入了标定的限制区域",),
                    )
                )

        red_light = self.config["red_light"]
        timestamp = frame_index / fps
        if (
            red_light["enabled"]
            and detection.class_name in red_light["target_classes"]
            and self._is_red_time(timestamp, red_light["red_intervals_seconds"])
            and self._crossed_stop_line(
                detection, history, red_light, frame_width, frame_height
            )
        ):
            candidates.append(
                (
                    "red_light",
                    int(red_light["severity"]),
                    "目标在配置的红灯时间段越过停止线",
                    float(red_light["cooldown_seconds"]),
                    3,
                    ("目标在配置的红灯时间段越过停止线",),
                )
            )

        operation = self.config.get("illegal_operation", {})
        if operation.get("enabled") and detection.class_name in operation.get(
            "vehicle_classes", []
        ):
            context = self._has_operation_context(
                detection,
                all_detections or [],
                history,
                operation,
                frame_width,
                frame_height,
            )
            if context:
                started_frame = self._operation_started_frame.setdefault(
                    detection.track_id, frame_index
                )
                observed_seconds = (frame_index - started_frame) / max(fps, 1.0)
                if (
                    observed_seconds >= float(operation["minimum_stop_seconds"])
                    and detection.track_id not in self._operation_episode_emitted
                ):
                    observation_count = (
                        self._operation_observations.get(detection.track_id, 0) + 1
                    )
                    high_risk = observation_count >= int(
                        operation["repeat_observations_for_high_risk"]
                    )
                    risk_level = 3 if high_risk else 2
                    severity = int(
                        operation[
                            "high_severity" if high_risk else "medium_severity"
                        ]
                    )
                    candidates.append(
                        (
                            "illegal_operation",
                            severity,
                            f"同一车辆第 {observation_count} 次出现低速停留并有人员接近的"
                            "疑似上下客线索，需核验车辆营运许可与现场记录。",
                            float(operation["cooldown_seconds"]),
                            risk_level,
                            (
                                "车辆连续低速或停留",
                                "车辆附近检测到人员",
                                f"累计观察 {observation_count} 次",
                            ),
                        )
                    )
            else:
                self._operation_started_frame.pop(detection.track_id, None)
                self._operation_episode_emitted.discard(detection.track_id)

        events: list[ViolationEvent] = []
        for (
            event_type,
            severity,
            message,
            cooldown_seconds,
            risk_level,
            evidence,
        ) in candidates:
            if self._cooldown_finished(
                event_type,
                detection.track_id,
                frame_index,
                fps,
                cooldown_seconds,
            ):
                if event_type == "illegal_operation":
                    self._operation_observations[detection.track_id] = (
                        self._operation_observations.get(detection.track_id, 0) + 1
                    )
                    self._operation_episode_emitted.add(detection.track_id)
                events.append(
                    ViolationEvent(
                        frame_index=frame_index,
                        timestamp_seconds=round(timestamp, 3),
                        track_id=detection.track_id,
                        class_name=detection.class_name,
                        event_type=event_type,
                        severity=severity,
                        message=message,
                        risk_level=risk_level,
                        review_required=True,
                        legal_conclusion=False,
                        evidence=evidence,
                    )
                )
        return events

    def is_suspicious(
        self,
        detection: Detection,
        history: TrackHistory,
        frame_index: int,
        fps: float,
        frame_width: int,
        frame_height: int,
        all_detections: list[Detection] | None = None,
    ) -> bool:
        """Return true only when this target is close to a configured violation."""

        wrong_way = self.config["wrong_way"]
        if (
            wrong_way["enabled"]
            and detection.class_name in wrong_way["target_classes"]
            and self._is_wrong_way_suspicious(detection, history, wrong_way)
        ):
            return True

        restricted = self.config["restricted_zone"]
        if restricted["enabled"] and detection.class_name in restricted[
            "prohibited_classes"
        ]:
            polygon = normalized_points_to_pixels(
                restricted["polygon"], frame_width, frame_height
            )
            if (
                not point_in_polygon(detection.center, polygon)
                and point_to_polygon_distance(detection.center, polygon)
                <= float(restricted.get("warning_margin_pixels", 30.0))
            ):
                return True

        red_light = self.config["red_light"]
        timestamp = frame_index / fps
        if (
            red_light["enabled"]
            and detection.class_name in red_light["target_classes"]
            and self._is_red_time(timestamp, red_light["red_intervals_seconds"])
            and self._near_stop_line(
                detection, history, red_light, frame_width, frame_height
            )
        ):
            return True

        operation = self.config.get("illegal_operation", {})
        return bool(
            operation.get("enabled")
            and detection.class_name in operation.get("vehicle_classes", [])
            and self._has_operation_context(
                detection,
                all_detections or [],
                history,
                operation,
                frame_width,
                frame_height,
            )
        )

    @staticmethod
    def _has_operation_context(
        detection: Detection,
        all_detections: list[Detection],
        history: TrackHistory,
        config: dict,
        width: int,
        height: int,
    ) -> bool:
        points = history.points(detection.track_id)
        minimum_history = int(config["minimum_history"])
        if len(points) < minimum_history:
            return False
        if vector_length(displacement(points[-minimum_history], points[-1])) > float(
            config["maximum_displacement_pixels"]
        ):
            return False
        person_classes = set(config["person_classes"])
        distance_limit = math.hypot(width, height) * float(
            config["person_proximity_ratio"]
        )
        return any(
            other.class_name in person_classes
            and math.dist(detection.center, other.center) <= distance_limit
            for other in all_detections
        )

    @staticmethod
    def _is_wrong_way(
        detection: Detection, history: TrackHistory, config: dict
    ) -> bool:
        points = history.points(detection.track_id)
        minimum_history = int(config["minimum_history"])
        if len(points) < minimum_history:
            return False
        start = points[-minimum_history]
        end = points[-1]
        motion = displacement(start, end)
        if vector_length(motion) < float(config["minimum_displacement_pixels"]):
            return False
        return cosine_similarity(motion, config["expected_direction"]) <= float(
            config["maximum_direction_cosine"]
        )

    @staticmethod
    def _is_wrong_way_suspicious(
        detection: Detection, history: TrackHistory, config: dict
    ) -> bool:
        points = history.points(detection.track_id)
        minimum_history = int(
            config.get(
                "warning_minimum_history",
                max(2, int(config["minimum_history"]) // 2),
            )
        )
        if len(points) < minimum_history:
            return False
        motion = displacement(points[-minimum_history], points[-1])
        minimum_displacement = float(
            config.get(
                "warning_minimum_displacement_pixels",
                float(config["minimum_displacement_pixels"]) / 2,
            )
        )
        if vector_length(motion) < minimum_displacement:
            return False
        similarity = cosine_similarity(motion, config["expected_direction"])
        return similarity <= float(config.get("suspicious_direction_cosine", 0.2))

    @staticmethod
    def _is_red_time(timestamp: float, intervals: list) -> bool:
        return any(float(start) <= timestamp <= float(end) for start, end in intervals)

    @staticmethod
    def _near_stop_line(
        detection: Detection,
        history: TrackHistory,
        config: dict,
        width: int,
        height: int,
    ) -> bool:
        points = history.points(detection.track_id)
        if len(points) < 2:
            return False
        movement = vector_length(displacement(points[-2], points[-1]))
        if movement < max(1.0, float(config["minimum_movement_pixels"]) / 2):
            return False
        line_start, line_end = normalized_points_to_pixels(
            config["stop_line"], width, height
        )
        return point_to_segment_distance(
            detection.center, line_start, line_end
        ) <= float(config.get("warning_distance_pixels", 40.0))

    @staticmethod
    def _crossed_stop_line(
        detection: Detection,
        history: TrackHistory,
        config: dict,
        width: int,
        height: int,
    ) -> bool:
        points = history.points(detection.track_id)
        if len(points) < 2:
            return False
        previous, current = points[-2], points[-1]
        if vector_length(displacement(previous, current)) < float(
            config["minimum_movement_pixels"]
        ):
            return False
        line_start, line_end = normalized_points_to_pixels(
            config["stop_line"], width, height
        )
        previous_side = signed_line_side(previous, line_start, line_end)
        current_side = signed_line_side(current, line_start, line_end)
        if math.isclose(previous_side, 0.0) and math.isclose(current_side, 0.0):
            return False
        return (
            previous_side == 0
            or current_side == 0
            or previous_side * current_side < 0
        )

    def _cooldown_finished(
        self,
        event_type: str,
        track_id: int,
        frame_index: int,
        fps: float,
        cooldown_seconds: float,
    ) -> bool:
        key = (event_type, track_id)
        last_frame = self._last_emitted_frame.get(key)
        cooldown_frames = max(1, round(cooldown_seconds * fps))
        if last_frame is not None and frame_index - last_frame < cooldown_frames:
            return False
        self._last_emitted_frame[key] = frame_index
        return True
