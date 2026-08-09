from collections.abc import Iterable, Sequence

import cv2
import numpy as np

from .geometry import normalized_points_to_pixels
from .models import Detection, FrameRisk, ViolationEvent
from .tracking import TrackHistory

LEVEL_COLORS = {
    "LOW": (50, 205, 50),
    "MEDIUM": (0, 215, 255),
    "HIGH": (0, 140, 255),
    "CRITICAL": (0, 0, 255),
}


def draw_scene(
    frame: np.ndarray,
    detections: Sequence[Detection],
    history: TrackHistory,
    events: Iterable[ViolationEvent],
    risk: FrameRisk,
    rules_config: dict,
) -> np.ndarray:
    height, width = frame.shape[:2]
    _draw_rule_regions(frame, rules_config, width, height)
    event_track_ids = {event.track_id for event in events if event.track_id >= 0}

    for detection in detections:
        color = (0, 0, 255) if detection.track_id in event_track_ids else (70, 220, 70)
        x1, y1, x2, y2 = detection.box
        cv2.rectangle(frame, (x1, y1), (x2, y2), color, 2)
        label = f"{detection.class_name} ID:{detection.track_id} {detection.confidence:.2f}"
        cv2.putText(
            frame,
            label,
            (x1, max(20, y1 - 7)),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.52,
            color,
            2,
            cv2.LINE_AA,
        )
        points = history.points(detection.track_id)
        if len(points) >= 2:
            cv2.polylines(
                frame,
                [np.asarray(points, dtype=np.int32)],
                False,
                color,
                2,
                cv2.LINE_AA,
            )

    _draw_status_panel(frame, risk)
    return frame


def _draw_rule_regions(frame: np.ndarray, config: dict, width: int, height: int) -> None:
    zone = config["restricted_zone"]
    if zone["enabled"]:
        polygon = np.asarray(
            normalized_points_to_pixels(zone["polygon"], width, height),
            dtype=np.int32,
        )
        overlay = frame.copy()
        cv2.fillPoly(overlay, [polygon], (0, 0, 255))
        cv2.addWeighted(overlay, 0.13, frame, 0.87, 0, frame)
        cv2.polylines(frame, [polygon], True, (0, 0, 255), 2)

    red = config["red_light"]
    if red["enabled"]:
        start, end = normalized_points_to_pixels(red["stop_line"], width, height)
        cv2.line(frame, start, end, (0, 0, 255), 3, cv2.LINE_AA)

    wrong_way = config["wrong_way"]
    if wrong_way["enabled"]:
        direction = wrong_way["expected_direction"]
        start = (70, 100)
        length = 60
        norm = max(0.001, float(np.hypot(direction[0], direction[1])))
        end = (
            int(start[0] + direction[0] / norm * length),
            int(start[1] + direction[1] / norm * length),
        )
        cv2.arrowedLine(frame, start, end, (255, 200, 0), 3, tipLength=0.25)
        cv2.putText(
            frame,
            "Expected direction",
            (15, 75),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.55,
            (255, 200, 0),
            2,
            cv2.LINE_AA,
        )


def _draw_status_panel(frame: np.ndarray, risk: FrameRisk) -> None:
    color = LEVEL_COLORS[risk.level]
    cv2.rectangle(frame, (10, 10), (355, 60), (25, 25, 25), -1)
    text = (
        f"Risk {risk.score:05.1f} {risk.level} | "
        f"objects {risk.tracked_objects} | events {risk.recent_events}"
    )
    cv2.putText(
        frame,
        text,
        (18, 43),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.55,
        color,
        2,
        cv2.LINE_AA,
    )
