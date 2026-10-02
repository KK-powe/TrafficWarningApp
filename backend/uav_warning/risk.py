from collections import deque
from collections.abc import Iterable

from .models import FrameRisk, ViolationEvent


class RiskScorer:
    def __init__(self, config: dict, fps: float):
        self.config = config
        self.fps = fps
        self._recent_events: deque[ViolationEvent] = deque()

    def update(
        self,
        frame_index: int,
        tracked_objects: int,
        new_events: Iterable[ViolationEvent],
        suspicious_objects: int = 0,
    ) -> FrameRisk:
        """Calculate risk from per-target states only.

        tracked_objects is kept for display/statistics and never changes the
        risk score. A concrete suspicious target produces MEDIUM risk, while a
        confirmed rule event produces at least HIGH risk.
        """
        self._recent_events.extend(
            event for event in new_events if event.track_id >= 0
        )
        window_frames = max(
            1, round(float(self.config["event_window_seconds"]) * self.fps)
        )
        while (
            self._recent_events
            and frame_index - self._recent_events[0].frame_index > window_frames
        ):
            self._recent_events.popleft()

        levels = self.config["levels"]
        weights = self.config["event_weights"]
        event_score = sum(
            float(weights.get(event.event_type, event.severity))
            for event in self._recent_events
        )

        high_risk_events = [
            event for event in self._recent_events if event.risk_level >= 3
        ]
        if high_risk_events:
            score = max(float(levels["high"]), event_score)
        elif self._recent_events:
            score = max(float(levels["medium"]), event_score)
        elif suspicious_objects > 0:
            score = float(levels["medium"])
        else:
            score = 0.0

        score = round(min(100.0, score), 2)
        return FrameRisk(
            frame_index=frame_index,
            timestamp_seconds=round(frame_index / self.fps, 3),
            score=score,
            level=self._level(score),
            tracked_objects=tracked_objects,
            recent_events=len(self._recent_events),
        )

    def _level(self, score: float) -> str:
        levels = self.config["levels"]
        if score >= float(levels["critical"]):
            return "CRITICAL"
        if score >= float(levels["high"]):
            return "HIGH"
        if score >= float(levels["medium"]):
            return "MEDIUM"
        return "LOW"
