import math

from .geometry import (
    cosine_similarity,
    displacement,
    normalized_points_to_pixels,
    point_in_polygon,
    signed_line_side,
    vector_length,
)
from .models import Detection, ViolationEvent
from .tracking import TrackHistory


class RuleEngine:
    def __init__(self, rules_config: dict):
        self.config = rules_config
        self._last_emitted_frame: dict[tuple[str, int], int] = {}

    def evaluate(
        self,
        detection: Detection,
        history: TrackHistory,
        frame_index: int,
        fps: float,
        frame_width: int,
        frame_height: int,
    ) -> list[ViolationEvent]:
        candidates: list[tuple[str, int, str, float]] = []

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
                )
            )

        events: list[ViolationEvent] = []
        for event_type, severity, message, cooldown_seconds in candidates:
            if self._cooldown_finished(
                event_type,
                detection.track_id,
                frame_index,
                fps,
                cooldown_seconds,
            ):
                events.append(
                    ViolationEvent(
                        frame_index=frame_index,
                        timestamp_seconds=round(timestamp, 3),
                        track_id=detection.track_id,
                        class_name=detection.class_name,
                        event_type=event_type,
                        severity=severity,
                        message=message,
                    )
                )
        return events

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
    def _is_red_time(timestamp: float, intervals: list) -> bool:
        return any(float(start) <= timestamp <= float(end) for start, end in intervals)

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
