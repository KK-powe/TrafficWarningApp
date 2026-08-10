from backend.analyzer import VideoAnalyzer
from backend.uav_warning.models import Detection


class FakeTensor:
    def __init__(self, values):
        self.values = values

    def int(self):
        return self

    def cpu(self):
        return self

    def tolist(self):
        return self.values


class FakeBoxes:
    def __init__(self, track_ids=None):
        self.xyxy = FakeTensor([[10.2, 20.2, 30.6, 40.6]])
        self.cls = FakeTensor([3])
        self.conf = FakeTensor([0.42])
        self.id = None if track_ids is None else FakeTensor(track_ids)

    def __len__(self):
        return 1


class FakeResult:
    def __init__(self, track_ids=None):
        self.boxes = FakeBoxes(track_ids)


def test_untracked_yolo_box_is_kept_for_drawing() -> None:
    detections = VideoAnalyzer._extract_detections(
        FakeResult(),
        {3: "car"},
        Detection,
    )

    assert len(detections) == 1
    assert detections[0].class_name == "car"
    assert detections[0].track_id == -1
    assert detections[0].box == (10, 20, 31, 41)


def test_tracked_yolo_box_keeps_its_track_id() -> None:
    detections = VideoAnalyzer._extract_detections(
        FakeResult([17]),
        {3: "car"},
        Detection,
    )

    assert detections[0].track_id == 17
