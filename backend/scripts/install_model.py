import argparse
import shutil
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser(description="安装YOLO11m 1280训练权重")
    parser.add_argument("source", type=Path, help="下载好的best.pt路径")
    arguments = parser.parse_args()

    source = arguments.source.expanduser().resolve()
    if not source.is_file():
        raise SystemExit(f"未找到权重文件：{source}")
    if source.suffix.lower() != ".pt":
        raise SystemExit("权重文件必须是.pt格式")

    destination = (
        Path(__file__).resolve().parents[1]
        / "weights"
        / "visdrone_yolo11m_1280_best.pt"
    )
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)
    print(f"权重已安装到：{destination}")


if __name__ == "__main__":
    main()
