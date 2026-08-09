#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../.."
python3 -m uvicorn backend.app:app --host 0.0.0.0 --port "${TRAFFIC_PORT:-6006}"
