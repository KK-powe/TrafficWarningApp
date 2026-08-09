from collections import defaultdict, deque

from .models import Detection, Point

TrackPoint = tuple[int, Point]


class TrackHistory:
    def __init__(self, history_length: int = 60, stale_after_frames: int = 90):
        self.history_length = history_length
        self.stale_after_frames = stale_after_frames
        self._points: dict[int, deque[TrackPoint]] = defaultdict(
            lambda: deque(maxlen=self.history_length)
        )
        self._last_seen: dict[int, int] = {}

    def add(self, detection: Detection, frame_index: int) -> None:
        self._points[detection.track_id].append((frame_index, detection.center))
        self._last_seen[detection.track_id] = frame_index

    def points(self, track_id: int) -> list[Point]:
        return [point for _, point in self._points.get(track_id, ())]

    def cleanup(self, current_frame: int) -> None:
        stale_ids = [
            track_id
            for track_id, last_seen in self._last_seen.items()
            if current_frame - last_seen > self.stale_after_frames
        ]
        for track_id in stale_ids:
            self._points.pop(track_id, None)
            self._last_seen.pop(track_id, None)
