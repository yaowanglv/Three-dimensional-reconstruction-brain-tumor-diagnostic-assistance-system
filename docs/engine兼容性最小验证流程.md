# .engine 兼容性最小验证流程

本文用于在 Jetson 设备上验证 YOLO TensorRT `.engine` 模型是否兼容当前运行环境，并确认能否接入现有 `detect-api.py` 分割服务。

## 1. 前置要求

在目标 Jetson 设备上执行验证。不要只在 PC 上验证，因为 TensorRT `.engine` 通常与设备、JetPack、CUDA、TensorRT 版本强相关。

准备文件：

- `best.engine`
- 一张测试图片，例如 `test.jpg`

建议把二者放到同一目录，便于执行命令。

## 2. 检查基础环境

```bash
python3 -c "import tensorrt as trt; print('TensorRT:', trt.__version__)"
python3 -c "import torch; print('Torch:', torch.__version__); print('CUDA:', torch.cuda.is_available())"
python3 -c "import ultralytics; print('Ultralytics:', ultralytics.__version__)"
cat /etc/nv_tegra_release
```

通过标准：

- `tensorrt` 能正常导入
- `torch.cuda.is_available()` 为 `True`
- `ultralytics` 能正常导入
- 能看到 JetPack/L4T 版本信息

## 3. 最小加载与推理测试

```bash
python3 - <<'PY'
from ultralytics import YOLO

model = YOLO("best.engine")
results = model.predict("test.jpg", imgsz=640, conf=0.25)

print("推理成功")
print("boxes:", results[0].boxes is not None)
print("masks:", results[0].masks is not None)
PY
```

通过标准：

- 命令不报错
- 输出 `推理成功`
- 如果是分割模型，`masks: True`

如果 `masks: False` 或 `masks: None`，说明该 engine 可能不是分割模型，或导出/后处理不符合当前 API 需要。

## 4. 验证是否适配 detect-api.py

现有 YOLO 分割服务通常依赖：

- `result.boxes`
- `result.masks`
- `result.masks.xy`
- `result.masks.xyn`

执行：

```bash
python3 - <<'PY'
from ultralytics import YOLO

model = YOLO("best.engine")
result = model.predict("test.jpg", imgsz=640, conf=0.25)[0]

print("boxes exists:", result.boxes is not None)
print("masks exists:", result.masks is not None)

if result.masks is not None:
    print("polygon count:", len(result.masks.xy))
    print("normalized polygon count:", len(result.masks.xyn))
    if len(result.masks.xy) > 0:
        print("first polygon shape:", result.masks.xy[0].shape)
else:
    raise SystemExit("不兼容：未返回 masks")

print("detect-api.py 所需字段验证通过")
PY
```

通过标准：

- `masks exists: True`
- `polygon count` 大于等于 0，且推理过程不报错
- 若测试图片中应有目标，`polygon count` 应大于 0
- 能正常访问 `result.masks.xy` 和 `result.masks.xyn`

## 5. 接入 Flask 服务验证

确认 `detect-api.py` 使用的 Python 环境与上面验证通过的环境一致。

启动服务：

```bash
cd /path/to/V11-dmt
python3 detect-api.py
```

健康检查：

```bash
curl http://localhost:5001/health
```

通过标准：

- 服务正常启动
- `/health` 返回正常响应

## 6. 调用分割接口验证

```bash
curl -X POST http://localhost:5001/detect \
  -F "file=@test.jpg" \
  -F "pt_path=/absolute/path/to/best.engine" \
  -F "conf=0.25" \
  -F "mode=segment" \
  -F "return_image=true" \
  -F "return_mask_images=true" \
  -F "save_result=false"
```

通过标准：

- JSON 中 `code` 为成功状态
- `data.detection.mode` 为 `segment`
- `data.detection.polygons` 字段存在
- `data.mask_image_base64` 存在
- `data.overlay_image_base64` 存在

## 7. 常见失败判断

### engine 加载失败

常见原因：

- `.engine` 不是在当前 Jetson 上导出的
- TensorRT / CUDA / JetPack 版本不匹配
- Python 环境缺少 TensorRT 绑定
- engine 的输入尺寸或动态 shape 与推理参数不兼容

建议处理：

- 在 Jetson 本机重新从 `.pt` 导出 `.engine`
- 确认导出和推理使用相同 `imgsz`

### 推理成功但 masks 为空

常见原因：

- 导出的是检测模型，不是分割模型
- 测试图片没有目标
- 置信度阈值过高
- 模型后处理不返回分割 mask

建议处理：

- 换一张确定有目标的测试图片
- 降低 `conf`
- 用 `.pt` 在同一图片上验证是否有 mask
- 重新导出 segmentation 模型的 `.engine`

## 8. 最小结论

只有当以下命令在 Jetson 上通过，才认为 `.engine` 与当前环境和现有 YOLO 分割 API 基本兼容：

```bash
python3 - <<'PY'
from ultralytics import YOLO
model = YOLO("best.engine")
result = model.predict("test.jpg", imgsz=640, conf=0.25)[0]
assert result.masks is not None
_ = result.masks.xy
_ = result.masks.xyn
print("ENGINE_COMPATIBLE")
PY
```
