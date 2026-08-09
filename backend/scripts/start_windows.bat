@echo off
cd /d "%~dp0\..\.."
if "%TRAFFIC_PORT%"=="" set TRAFFIC_PORT=6006
python -m uvicorn backend.app:app --host 0.0.0.0 --port %TRAFFIC_PORT%
