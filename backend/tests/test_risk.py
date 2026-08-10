from backend.uav_warning.models import ViolationEvent
from backend.uav_warning.risk import RiskScorer


CONFIG = {
    "event_window_seconds": 3,
    "event_weights": {
        "wrong_way": 25,
        "restricted_zone": 20,
        "red_light": 40,
    },
    "levels": {"medium": 25, "high": 50, "critical": 75},
}


def test_vehicle_count_does_not_change_risk() -> None:
    risk = RiskScorer(CONFIG, fps=25).update(
        frame_index=0,
        tracked_objects=300,
        new_events=[],
    )
    assert risk.score == 0
    assert risk.level == "LOW"
    assert risk.tracked_objects == 300


def test_specific_suspicious_target_is_medium_risk() -> None:
    risk = RiskScorer(CONFIG, fps=25).update(
        frame_index=0,
        tracked_objects=300,
        new_events=[],
        suspicious_objects=1,
    )
    assert risk.score == 25
    assert risk.level == "MEDIUM"


def test_confirmed_violation_is_at_least_high_risk() -> None:
    event = ViolationEvent(
        frame_index=0,
        timestamp_seconds=0,
        track_id=12,
        class_name="motor",
        event_type="restricted_zone",
        severity=20,
        message="目标进入了标定的限制区域",
    )
    risk = RiskScorer(CONFIG, fps=25).update(
        frame_index=0,
        tracked_objects=300,
        new_events=[event],
    )
    assert risk.score == 50
    assert risk.level == "HIGH"
