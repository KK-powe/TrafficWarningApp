import os
from dataclasses import dataclass
from pathlib import Path


BACKEND_ROOT = Path(__file__).resolve().parent
PROJECT_ROOT = BACKEND_ROOT.parent


@dataclass(frozen=True)
class Settings:
    model_path: Path
    config_path: Path
    uploads_dir: Path
    results_dir: Path
    max_upload_bytes: int
    worker_count: int

    @classmethod
    def from_environment(cls) -> "Settings":
        model_path = Path(
            os.getenv(
                "TRAFFIC_MODEL_PATH",
                BACKEND_ROOT / "weights" / "visdrone_yolo11m_1280_best.pt",
            )
        ).expanduser()
        config_path = Path(
            os.getenv(
                "TRAFFIC_CONFIG_PATH", BACKEND_ROOT / "configs" / "default.yaml"
            )
        ).expanduser()
        storage_root = Path(
            os.getenv("TRAFFIC_STORAGE_DIR", BACKEND_ROOT / "data")
        ).expanduser()
        return cls(
            model_path=model_path.resolve(),
            config_path=config_path.resolve(),
            uploads_dir=(storage_root / "uploads").resolve(),
            results_dir=(storage_root / "results").resolve(),
            max_upload_bytes=int(os.getenv("TRAFFIC_MAX_UPLOAD_MB", "500"))
            * 1024
            * 1024,
            worker_count=max(1, int(os.getenv("TRAFFIC_WORKERS", "1"))),
        )

    def prepare_directories(self) -> None:
        self.uploads_dir.mkdir(parents=True, exist_ok=True)
        self.results_dir.mkdir(parents=True, exist_ok=True)
