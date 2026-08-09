import json
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from pathlib import Path

from .analyzer import VideoAnalyzer
from .settings import Settings


@dataclass
class TaskRecord:
    task_id: str
    input_path: Path
    result_dir: Path
    created_at: int
    status: str = "queued"
    progress: int = 0
    error: str | None = None
    result: dict | None = None
    updated_at: int = field(default_factory=lambda: int(time.time() * 1000))

    def status_payload(self) -> dict:
        return {
            "taskId": self.task_id,
            "status": self.status,
            "progress": self.progress,
            "createdAt": self.created_at,
            "updatedAt": self.updated_at,
            "error": self.error,
        }


class TaskManager:
    """Run video-analysis jobs in a small background worker pool."""

    def __init__(self, settings: Settings, analyzer: VideoAnalyzer | None = None):
        self.settings = settings
        self.settings.prepare_directories()
        self.analyzer = analyzer or VideoAnalyzer(settings)
        self._executor = ThreadPoolExecutor(
            max_workers=settings.worker_count, thread_name_prefix="traffic-analysis"
        )
        self._lock = threading.RLock()
        self._tasks: dict[str, TaskRecord] = {}
        self._load_completed_results()

    def submit(self, task_id: str, input_path: Path, created_at: int) -> TaskRecord:
        record = TaskRecord(
            task_id=task_id,
            input_path=input_path,
            result_dir=self.settings.results_dir / task_id,
            created_at=created_at,
        )
        with self._lock:
            self._tasks[task_id] = record
        self._executor.submit(self._run, task_id)
        return record

    def get(self, task_id: str) -> TaskRecord | None:
        with self._lock:
            return self._tasks.get(task_id)

    def completed(self) -> list[TaskRecord]:
        with self._lock:
            return sorted(
                (item for item in self._tasks.values() if item.status == "completed"),
                key=lambda item: item.created_at,
                reverse=True,
            )

    def latest_completed(self) -> TaskRecord | None:
        items = self.completed()
        return items[0] if items else None

    def update_review(self, event_id: str, status: int) -> dict | None:
        with self._lock:
            for record in self._tasks.values():
                if not record.result:
                    continue
                for event in record.result.get("events", []):
                    if event.get("id") == event_id:
                        event["reviewStatus"] = status
                        self._persist_result(record)
                        return event
        return None

    def _run(self, task_id: str) -> None:
        self._update(task_id, status="processing", progress=1, error=None)
        record = self.get(task_id)
        if record is None:
            return
        try:
            result = self.analyzer.analyze(
                input_path=record.input_path,
                result_dir=record.result_dir,
                task_id=record.task_id,
                created_at_ms=record.created_at,
                progress_callback=lambda value: self._update(
                    task_id, progress=max(1, min(100, int(value)))
                ),
            )
            self._update(
                task_id,
                status="completed",
                progress=100,
                result=result,
                error=None,
            )
        except Exception as exc:  # Error is exposed through the task-status API.
            self._update(task_id, status="failed", error=str(exc))

    def _update(self, task_id: str, **changes) -> None:
        with self._lock:
            record = self._tasks.get(task_id)
            if record is None:
                return
            for name, value in changes.items():
                setattr(record, name, value)
            record.updated_at = int(time.time() * 1000)

    def _load_completed_results(self) -> None:
        for result_file in self.settings.results_dir.glob("*/analysis.json"):
            try:
                with result_file.open(encoding="utf-8") as file:
                    result = json.load(file)
                task_id = str(result["taskId"])
                created_at = int(result.get("createdAt", 0))
                self._tasks[task_id] = TaskRecord(
                    task_id=task_id,
                    input_path=self.settings.uploads_dir / f"{task_id}.mp4",
                    result_dir=result_file.parent,
                    created_at=created_at,
                    status="completed",
                    progress=100,
                    result=result,
                )
            except (OSError, ValueError, KeyError, TypeError):
                continue

    @staticmethod
    def _persist_result(record: TaskRecord) -> None:
        if record.result is None:
            return
        record.result_dir.mkdir(parents=True, exist_ok=True)
        with (record.result_dir / "analysis.json").open("w", encoding="utf-8") as file:
            json.dump(record.result, file, ensure_ascii=False, indent=2)
