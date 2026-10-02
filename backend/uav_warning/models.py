from dataclasses import asdict, dataclass

Point = tuple[int, int]
Box = tuple[int, int, int, int]


@dataclass(frozen=True)
class Detection:
    track_id: int
    class_id: int
    class_name: str
    confidence: float
    box: Box
    center: Point


@dataclass(frozen=True)
class ViolationEvent:
    frame_index: int
    timestamp_seconds: float
    track_id: int
    class_name: str
    event_type: str
    severity: int
    message: str
    risk_level: int = 3
    review_required: bool = True
    legal_conclusion: bool = False
    evidence: tuple[str, ...] = ()

    def to_dict(self) -> dict:
        return asdict(self)


@dataclass(frozen=True)
class FrameRisk:
    frame_index: int
    timestamp_seconds: float
    score: float
    level: str
    tracked_objects: int
    recent_events: int

    def to_dict(self) -> dict:
        return asdict(self)
