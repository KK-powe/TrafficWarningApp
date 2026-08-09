#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../.."
export TRAFFIC_MODEL_PATH="${TRAFFIC_MODEL_PATH:-$PWD/backend/weights/visdrone_yolo11m_1280_best.pt}"
export TRAFFIC_STORAGE_DIR="${TRAFFIC_STORAGE_DIR:-/root/autodl-tmp/traffic-warning-data}"
export TRAFFIC_WORKERS="${TRAFFIC_WORKERS:-1}"
python -m uvicorn backend.app:app --host 0.0.0.0 --port "${TRAFFIC_PORT:-6006}"
