# Python 推理服务

本目录提供三个 Flask 服务，由 Spring Boot 转发调用。

| 文件 | 端口 | 作用 |
|------|------|------|
| `detect-api.py` | 5001 | YOLO 检测 / 实例分割 |
| `unet4engine.py` | 3408 | UNet 切片分割，供三维重建使用 |
| `mesh-api.py` | 5002 | 从分割体积重建脑/肿瘤网格 |

`ultralytics/` 为训练时使用的定制 fork，加载 `models/yolo/**/*.pt` 时需要它，不要用官方 `pip install ultralytics` 覆盖。

## 权重

相对本仓库根目录：

```
models/yolo/detect/YOLOv11/best.pt
models/yolo/detect/YOLOv8/best.pt
models/yolo/mask/YOLO11/best.pt
models/unet/brats/checkpoint_epoch100.pth
```

前端「系统配置」里扫描上述文件夹即可。UNet `.pth` 通过 Git LFS 分发，clone 后需安装 Git LFS。

## 启动

```bash
cd detect_service
python -m venv venv
# Windows: venv\Scripts\activate
pip install -r requirements.txt
# 可选 GPU: 按本机 CUDA 安装 torch

python detect-api.py
python unet4engine.py --host 0.0.0.0 --port 3408
python mesh-api.py
```

Windows 也可双击 `start_detect.bat` / `start_unet.bat` / `start_mesh.bat`。
