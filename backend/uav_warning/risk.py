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
    ) -> FrameRisk:
        self._recent_events.extend(new_events)
        window_frames = max(
            1, round(float(self.config["event_window_seconds"]) * self.fps)
        )
        while (
            self._recent_events
            and frame_index - self._recent_events[0].frame_index > window_frames
        ):
            self._recent_events.popleft()

        density_target = max(1, int(self.config["density_target_count"]))
        density_score = min(
            float(self.config["density_max_score"]),
            tracked_objects
            / density_target
            * float(self.config["density_max_score"]),
        )
        weights = self.config["event_weights"]
        event_score = sum(
            float(weights.get(event.event_type, event.severity))
            for event in self._recent_events
        )
        score = round(min(100.0, density_score + event_score), 2)
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
