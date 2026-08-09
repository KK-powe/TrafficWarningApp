import time
import uuid
from pathlib import Path

from fastapi import FastAPI, File, HTTPException, Request, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from .settings import Settings
from .task_manager import TaskManager, TaskRecord


ALLOWED_VIDEO_SUFFIXES = {".mp4", ".mov", ".avi", ".mkv", ".m4v"}


def api_response(data=None, message: str = "success", code: int = 200) -> dict:
    return {"code": code, "message": message, "data": data}


def create_app(
    settings: Settings | None = None, task_manager: TaskManager | None = None
) -> FastAPI:
    active_settings = settings or Settings.from_environment()
    active_settings.prepare_directories()
    manager = task_manager or TaskManager(active_settings)

    application = FastAPI(
        title="无人机交通风险预警 API",
        version="1.0.0",
        description="上传交通视频，使用YOLO11m + ByteTrack进行目标检测、跟踪与风险分析。",
    )
    application.state.settings = active_settings
    application.state.task_manager = manager
    application.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=False,
        allow_methods=["*"],
        allow_headers=["*"],
    )
    application.mount(
        "/files", StaticFiles(directory=str(active_settings.results_dir)), name="files"
    )

    @application.get("/health")
    def health() -> dict:
        return api_response(
            {
                "status": "ok",
                "modelReady": active_settings.model_path.is_file(),
                "modelPath": str(active_settings.model_path),
                "workers": active_settings.worker_count,
            }
        )

    @application.post("/api/tasks", status_code=202)
    async def create_task(video: UploadFile = File(...)) -> dict:
        suffix = Path(video.filename or "").suffix.lower()
        if suffix not in ALLOWED_VIDEO_SUFFIXES:
            raise HTTPException(
                status_code=400,
                detail="仅支持 mp4、mov、avi、mkv、m4v 视频文件",
            )
        task_id = uuid.uuid4().hex
        target = active_settings.uploads_dir / f"{task_id}{suffix}"
        size = 0
        try:
            with target.open("wb") as output:
                while chunk := await video.read(1024 * 1024):
                    size += len(chunk)
                    if size > active_settings.max_upload_bytes:
                        raise HTTPException(
                            status_code=413,
                            detail="视频超过服务器允许的最大上传大小",
                        )
                    output.write(chunk)
        except Exception:
            target.unlink(missing_ok=True)
            raise
        finally:
            await video.close()

        if size == 0:
            target.unlink(missing_ok=True)
            raise HTTPException(status_code=400, detail="上传的视频为空")

        created_at = int(time.time() * 1000)
        record = manager.submit(task_id, target, created_at)
        return api_response(record.status_payload(), "视频已上传，等待分析")

    @application.get("/api/tasks/{task_id}")
    def get_task(task_id: str) -> dict:
        record = manager.get(task_id)
        if record is None:
            raise HTTPException(status_code=404, detail="任务不存在")
        return api_response(record.status_payload())

    @application.get("/api/tasks/{task_id}/result")
    def get_task_result(task_id: str, request: Request) -> dict:
        record = manager.get(task_id)
        if record is None:
            raise HTTPException(status_code=404, detail="任务不存在")
        if record.status == "failed":
            return api_response(None, record.error or "分析失败", 500)
        if record.status != "completed" or record.result is None:
            return api_response(record.status_payload(), "任务尚未完成", 202)
        return api_response(_result_with_urls(record, request))

    @application.get("/api/analysis/realtime")
    def realtime(request: Request) -> dict:
        record = manager.latest_completed()
        if record is None:
            return api_response(None, "暂无已完成的分析结果", 204)
        return api_response(_result_with_urls(record, request))

    @application.get("/api/analysis/history")
    def history(request: Request) -> dict:
        records = manager.completed()
        if not records:
            return api_response(None, "暂无历史分析结果", 204)
        latest = _result_with_urls(records[0], request)
        latest["history"] = [
            {
                "taskId": item.task_id,
                "createdAt": item.created_at,
                "stats": item.result.get("stats", {}) if item.result else {},
            }
            for item in records
        ]
        return api_response(latest)

    @application.post("/api/review/{event_id}/{status}")
    def review(event_id: str, status: int) -> dict:
        if status not in (1, 2):
            raise HTTPException(status_code=400, detail="复核状态只能是1或2")
        event = manager.update_review(event_id, status)
        if event is None:
            raise HTTPException(status_code=404, detail="预警事件不存在")
        return api_response(event, "复核结果已保存")

    return application


def _result_with_urls(record: TaskRecord, request: Request) -> dict:
    result = dict(record.result or {})
    base_url = str(request.base_url).rstrip("/")
    file_base = f"{base_url}/files/{record.task_id}"
    result["annotatedImageUrl"] = f"{file_base}/{result['annotatedImageFile']}"
    result["resultVideoUrl"] = f"{file_base}/{result['resultVideoFile']}"
    events = []
    for original in result.get("events", []):
        event = dict(original)
        event["frameImageUrl"] = f"{file_base}/{event['frameImageFile']}"
        events.append(event)
    result["events"] = events
    return result


app = create_app()
