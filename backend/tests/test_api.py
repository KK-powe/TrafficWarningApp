import json
import time
from pathlib import Path

from fastapi.testclient import TestClient

from backend.app import create_app
from backend.settings import Settings
from backend.task_manager import TaskManager


class FakeAnalyzer:
    def analyze(
        self,
        input_path: Path,
        result_dir: Path,
        task_id: str,
        created_at_ms: int,
        progress_callback,
    ) -> dict:
        assert input_path.read_bytes() == b"fake-video"
        result_dir.mkdir(parents=True, exist_ok=True)
        (result_dir / "events").mkdir(exist_ok=True)
        (result_dir / "preview.jpg").write_bytes(b"jpg")
        (result_dir / "annotated.mp4").write_bytes(b"mp4")
        (result_dir / "events" / "event.jpg").write_bytes(b"event")
        progress_callback(50)
        result = {
            "taskId": task_id,
            "createdAt": created_at_ms,
            "stats": {
                "totalWarnings": 1,
                "totalTracked": 2,
                "highRiskCount": 0,
                "riskLevel": 2,
            },
            "events": [
                {
                    "id": f"{task_id}-0001",
                    "type": "交通目标密度较高",
                    "riskLevel": 2,
                    "timestamp": created_at_ms,
                    "frameImageFile": "events/event.jpg",
                    "description": "测试事件",
                    "targetId": "-",
                    "targetClass": "all",
                    "location": "上传视频画面",
                    "reviewStatus": 0,
                }
            ],
            "annotatedImageFile": "preview.jpg",
            "resultVideoFile": "annotated.mp4",
        }
        (result_dir / "analysis.json").write_text(
            json.dumps(result, ensure_ascii=False), encoding="utf-8"
        )
        progress_callback(100)
        return result


def build_client(tmp_path: Path) -> TestClient:
    settings = Settings(
        model_path=tmp_path / "best.pt",
        config_path=tmp_path / "config.yaml",
        uploads_dir=tmp_path / "uploads",
        results_dir=tmp_path / "results",
        max_upload_bytes=1024,
        worker_count=1,
    )
    manager = TaskManager(settings, analyzer=FakeAnalyzer())
    return TestClient(create_app(settings, manager))


def wait_until_finished(client: TestClient, task_id: str) -> dict:
    for _ in range(100):
        payload = client.get(f"/api/tasks/{task_id}").json()
        if payload["data"]["status"] in ("completed", "failed"):
            return payload
        time.sleep(0.01)
    raise AssertionError("分析任务没有按时完成")


def test_health_and_video_task_flow(tmp_path: Path) -> None:
    client = build_client(tmp_path)
    health = client.get("/health")
    assert health.status_code == 200
    assert health.json()["data"]["modelReady"] is False

    upload = client.post(
        "/api/tasks",
        files={"video": ("sample.mp4", b"fake-video", "video/mp4")},
    )
    assert upload.status_code == 202
    task_id = upload.json()["data"]["taskId"]

    status = wait_until_finished(client, task_id)
    assert status["data"]["status"] == "completed"
    assert status["data"]["progress"] == 100

    result = client.get(f"/api/tasks/{task_id}/result").json()
    assert result["code"] == 200
    assert result["data"]["stats"]["totalTracked"] == 2
    assert result["data"]["resultVideoUrl"].endswith("/annotated.mp4")
    assert result["data"]["events"][0]["frameImageUrl"].endswith("/events/event.jpg")

    event_id = result["data"]["events"][0]["id"]
    review = client.post(f"/api/review/{event_id}/1")
    assert review.status_code == 200
    assert review.json()["data"]["reviewStatus"] == 1

    latest = client.get("/api/analysis/realtime").json()
    assert latest["data"]["taskId"] == task_id


def test_rejects_unsupported_file(tmp_path: Path) -> None:
    client = build_client(tmp_path)
    response = client.post(
        "/api/tasks", files={"video": ("notes.txt", b"not-video", "text/plain")}
    )
    assert response.status_code == 400


def test_rejects_oversized_upload(tmp_path: Path) -> None:
    client = build_client(tmp_path)
    response = client.post(
        "/api/tasks",
        files={"video": ("large.mp4", b"x" * 2048, "video/mp4")},
    )
    assert response.status_code == 413
    assert not list((tmp_path / "uploads").glob("*"))
