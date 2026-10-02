from collections.abc import Iterable, Sequence

import cv2
import numpy as np

from .geometry import normalized_points_to_pixels
from .models import Detection, FrameRisk, ViolationEvent
from .tracking import TrackHistory

LEVEL_COLORS = {
    "LOW": (50, 205, 50),
    "MEDIUM": (0, 255, 255),
    "HIGH": (0, 0, 255),
    "CRITICAL": (0, 0, 190),
}

EVENT_LABELS = {
    "wrong_way": "WRONG WAY",
    "restricted_zone": "RESTRICTED ZONE",
    "red_light": "RED LIGHT",
    "illegal_operation": "OPERATION RISK",
}


def draw_scene(
    frame: np.ndarray,
    detections: Sequence[Detection],
    history: TrackHistory,
    events: Iterable[ViolationEvent],
    risk: FrameRisk,
    rules_config: dict,
    active_violation_track_ids: Iterable[int] | None = None,
    suspicious_track_ids: Iterable[int] | None = None,
) -> np.ndarray:
    height, width = frame.shape[:2]
    frame_events = list(events)
    _draw_rule_regions(frame, rules_config, width, height)

    violation_track_ids = set(active_violation_track_ids or ())
    violation_track_ids.update(
        event.track_id
        for event in frame_events
        if event.track_id >= 0 and event.risk_level >= 3
    )
    suspicious_ids = set(suspicious_track_ids or ())
    suspicious_ids.update(
        event.track_id
        for event in frame_events
        if event.track_id >= 0 and event.risk_level == 2
    )
    suspicious_ids.difference_update(violation_track_ids)

    for detection in detections:
        if detection.track_id in violation_track_ids:
            color = LEVEL_COLORS["HIGH"]
            thickness = 3
        elif detection.track_id in suspicious_ids:
            color = LEVEL_COLORS["MEDIUM"]
            thickness = 3
        else:
            color = LEVEL_COLORS["LOW"]
            thickness = 3

        x1, y1, x2, y2 = detection.box
        cv2.rectangle(frame, (x1, y1), (x2, y2), color, thickness)
        track_label = f"ID:{detection.track_id}" if detection.track_id >= 0 else "ID:--"
        label = f"{detection.class_name} {track_label} {detection.confidence:.2f}"
        cv2.putText(
            frame,
            label,
            (x1, max(20, y1 - 7)),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.60,
            color,
            2,
            cv2.LINE_AA,
        )
        if detection.track_id >= 0:
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

    _draw_status_panel(frame, risk, frame_events, len(suspicious_ids))
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


def _draw_status_panel(
    frame: np.ndarray,
    risk: FrameRisk,
    events: Sequence[ViolationEvent],
    suspicious_count: int,
) -> None:
    height, width = frame.shape[:2]
    color = LEVEL_COLORS.get(risk.level, LEVEL_COLORS["LOW"])
    warning_active = risk.level != "LOW" or bool(events) or suspicious_count > 0

    if warning_active:
        overlay = frame.copy()
        banner_height = min(92, max(64, height // 8))
        cv2.rectangle(overlay, (0, 0), (width, banner_height), (20, 20, 20), -1)
        cv2.addWeighted(overlay, 0.78, frame, 0.22, 0, frame)
        cv2.rectangle(frame, (2, 2), (width - 3, height - 3), color, 6)

        title = f"{risk.level} RISK WARNING  SCORE {risk.score:05.1f}"
        cv2.putText(
            frame,
            title,
            (18, 36),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.78,
            color,
            2,
            cv2.LINE_AA,
        )
        event_names = sorted(
            {
                EVENT_LABELS.get(event.event_type, event.event_type.upper())
                for event in events
            }
        )
        if event_names:
            detail = " / ".join(event_names)
        elif suspicious_count > 0:
            detail = f"SUSPICIOUS TARGETS {suspicious_count}"
        else:
            detail = "RISK REMAINS ACTIVE"
        detail += (
            f" | objects {risk.tracked_objects} | recent events {risk.recent_events}"
        )
        cv2.putText(
            frame,
            detail,
            (18, min(banner_height - 14, 70)),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.55,
            (255, 255, 255),
            2,
            cv2.LINE_AA,
        )
        return

    panel_width = min(width - 20, 420)
    cv2.rectangle(frame, (10, 10), (10 + panel_width, 60), (25, 25, 25), -1)
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
