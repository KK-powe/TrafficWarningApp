from backend.uav_warning.models import Detection
from backend.uav_warning.rules import RuleEngine
from backend.uav_warning.tracking import TrackHistory


def build_config() -> dict:
    return {
        "wrong_way": {
            "enabled": False,
            "target_classes": ["motor"],
            "expected_direction": [1.0, 0.0],
            "minimum_history": 8,
            "minimum_displacement_pixels": 25,
            "maximum_direction_cosine": -0.25,
            "cooldown_seconds": 3,
            "severity": 25,
        },
        "restricted_zone": {
            "enabled": True,
            "polygon": [[0.4, 0.4], [0.6, 0.4], [0.6, 0.6], [0.4, 0.6]],
            "prohibited_classes": ["car"],
            "warning_margin_pixels": 10,
            "cooldown_seconds": 3,
            "severity": 20,
        },
        "red_light": {
            "enabled": False,
            "stop_line": [[0.2, 0.5], [0.8, 0.5]],
            "red_intervals_seconds": [[0, 10]],
            "target_classes": ["car"],
            "minimum_movement_pixels": 8,
            "cooldown_seconds": 5,
            "severity": 40,
        },
    }


def detection(track_id: int, center: tuple[int, int]) -> Detection:
    x, y = center
    return Detection(
        track_id=track_id,
        class_id=3,
        class_name="car",
        confidence=0.9,
        box=(x - 2, y - 2, x + 2, y + 2),
        center=center,
    )


def test_only_target_near_configured_zone_is_suspicious() -> None:
    engine = RuleEngine(build_config())
    history = TrackHistory()
    nearby = detection(1, (35, 50))
    distant = detection(2, (10, 10))
    history.add(nearby, 0)
    history.add(distant, 0)

    assert engine.is_suspicious(nearby, history, 0, 25, 100, 100)
    assert not engine.is_suspicious(distant, history, 0, 25, 100, 100)


def test_target_inside_zone_generates_confirmed_violation() -> None:
    engine = RuleEngine(build_config())
    history = TrackHistory()
    inside = detection(7, (50, 50))
    history.add(inside, 0)

    events = engine.evaluate(inside, history, 0, 25, 100, 100)

    assert len(events) == 1
    assert events[0].track_id == 7
    assert events[0].event_type == "restricted_zone"
