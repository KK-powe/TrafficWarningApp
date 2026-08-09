# 无人机交通风险预警 Android + FastAPI

这是一个大创原型系统：Android 端上传无人机或道路交通视频，FastAPI 后端调用训练好的 YOLO11m 1280 模型检测目标，并使用 ByteTrack 跟踪，最终返回标注图片、标注视频、交通目标统计和风险预警事件。

## 已实现功能

- Android 选择并上传视频；
- 后端异步任务队列，避免长视频请求一直阻塞；
- 查询排队、分析进度和失败原因；
- YOLO11m 1280 + ByteTrack 检测和跟踪；
- 标注视频、预览图片、事件截图和 JSON 结果；
- Android 展示统计、事件、预览图并打开结果视频；
- 支持普通电脑和 AutoDL 两种后端启动方式。

## 项目结构

```text
app/                         Android Java 客户端
backend/
  app.py                     FastAPI接口
  analyzer.py                YOLO视频分析入口
  task_manager.py            后台任务队列
  configs/default.yaml       模型、风险和交通规则配置
  scripts/                   权重安装与启动脚本
  uav_warning/               跟踪、规则、风险评分和绘图
```

## 1. 放置模型权重

模型权重不上传到 GitHub。请把训练得到的 `best.pt` 安装到默认位置：

```bash
python3 backend/scripts/install_model.py "/你的路径/visdrone_yolo11m_1280_best.pt"
```

安装后应存在：

```text
backend/weights/visdrone_yolo11m_1280_best.pt
```

也可以不复制文件，启动前设置环境变量 `TRAFFIC_MODEL_PATH` 指向权重的绝对路径。

本项目使用的是实际训练得到的 YOLO11m 1280 权重。现有验证记录为 mAP50 0.5877、mAP50-95 0.3705；指标只代表 VisDrone 验证集结果，不等于所有实际道路视频上的准确率。

## 2. 普通电脑启动后端

建议使用 Python 3.10 或 3.11。第一次运行：

```bash
python3 -m venv .venv
source .venv/bin/activate
python3 -m pip install -r backend/requirements.txt
python3 backend/scripts/install_model.py "/你的路径/best.pt"
```

macOS / Linux 启动：

```bash
bash backend/scripts/start_mac_linux.sh
```

Windows 双击 `backend/scripts/start_windows.bat`，或在 PyCharm/VS Code 中运行 `backend/app.py` 对应的 Uvicorn 配置。

启动成功后打开：

- 健康检查：`http://127.0.0.1:6006/health`
- API 调试页面：`http://127.0.0.1:6006/docs`

没有 NVIDIA 显卡时会自动使用 Apple MPS 或 CPU。CPU 可以演示，但处理 1280 视频会明显更慢。

## 3. AutoDL 启动后端

把仓库和权重放到数据盘后，在仓库目录执行：

```bash
python -m pip install -r backend/requirements.txt
python backend/scripts/install_model.py "/root/autodl-tmp/你的best.pt"
bash backend/scripts/start_autodl.sh
```

建议用 `screen` 保持后台运行：

```bash
screen -S traffic_api
bash backend/scripts/start_autodl.sh
```

按 `Ctrl+A`，再按 `D` 退出 screen，服务仍会运行。然后在 AutoDL 控制台把容器端口 `6006` 配置为“自定义服务/公网访问”，把生成的 HTTPS 地址填到 Android 设置页。实例关机或释放后，AutoDL 地址和后端都会不可用。

## 4. Android 运行与服务器地址

推荐运行方式：

1. 用 Android Studio 打开仓库根目录；
2. 等待 Gradle Sync 完成；
3. 选择模拟器或真机；
4. 点击绿色三角形 Run；
5. 进入“设置”，填写服务器地址并保存；
6. 回到实时页，点击“选择并上传视频”。

地址填写规则：

- Android 模拟器连接本机：`http://10.0.2.2:6006/`
- 真机连接同一 Wi-Fi 下的电脑：`http://电脑局域网IP:6006/`
- AutoDL：填写自定义服务生成的完整 HTTPS 地址。

真机不能用 `127.0.0.1` 连接电脑，因为手机里的 `127.0.0.1` 指向手机自己。

## 5. API 流程

```text
POST /api/tasks                    上传视频，返回 taskId
GET  /api/tasks/{taskId}           查询状态和0-100进度
GET  /api/tasks/{taskId}/result    获取完整分析结果
GET  /api/analysis/realtime        获取最近一次完成结果
GET  /api/analysis/history         获取历史结果摘要
POST /api/review/{eventId}/{status} 复核事件，1确认、2误报
```

后端默认只开一个分析 worker，避免多个任务同时抢占一张 GPU。上传文件、结果和权重都已加入 `.gitignore`。

## 6. 关于交通违法规则

目标检测和跟踪可以直接运行；逆行、限制区域、闯红灯依赖具体摄像机画面中的行驶方向、道路多边形、停止线和红灯时段。因此这些规则在 `backend/configs/default.yaml` 中默认关闭，完成视频标定后再开启。未标定时系统仍会输出目标数量、跟踪结果和高密度风险提示，不会把未验证的规则判断冒充真实违法结论。

## 后端测试

```bash
python3 -m pip install -r backend/requirements-dev.txt
python3 -m pytest backend/tests
```

Android 构建检查：

```bash
./gradlew test
./gradlew assembleDebug
```
