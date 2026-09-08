# UNet 分割接口开发文档

## 1. 服务概述

- **服务名称**：unet4threedim
- **运行端口**：3408（PC 开发），Jetson 上监听 `127.0.0.1:3408`
- **基础框架**：Flask
- **核心依赖**：PyTorch, Pillow, numpy, opencv-python, flask-cors
- **Jetson 扩展依赖**：tensorrt, pycuda（用于 TensorRT 加速）

## 2. 服务部署

### 2.1 启动命令

```bash
# PC 开发环境
python unet4threedim.py --port 3408

# Jetson 生产环境（只监听本地）
python unet4threedim.py --port 3408 --host 127.0.0.1 --config config-jetson.yml
```

### 2.2 环境变量

| 变量名 | 默认值 | 说明 |
| :--- | :--- | :--- |
| `UNET_PORT` | 3408 | 服务端口 |
| `UNET_HOST` | 0.0.0.0 | 监听地址（Jetson 设为 127.0.0.1）|
| `UNET_MODEL_DIR` | ./models | 模型文件默认目录 |
| `UNET_DEVICE` | cuda | 运行设备 (cuda/cpu) |
| `UNET_CONFIG` | config.yml | 配置文件路径 |

### 2.3 Jetson 部署步骤

```bash
# 1. 安装 Jetson 专用 PyTorch
# 参考: https://forums.developer.nvidia.com/t/pytorch-for-jetson

# 2. 安装依赖
pip install flask flask-cors pillow numpy opencv-python

# 3. 安装 TensorRT Python API（JetPack 自带）
# 无需额外安装

# 4. 启动服务
python unet4threedim.py --host 127.0.0.1 --config config-jetson.yml
```

## 3. API 接口定义

### 3.1 健康检查 `/health`

```http
GET /health
```

**返回结构**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "status": "ok",
    "service": "unet4threedim",
    "version": "1.0.0",
    "device": "cuda",
    "gpu_memory": "2048MB / 4096MB",
    "timestamp": "2024-01-15T10:30:00"
  }
}
```

### 3.2 单张图像分割 `/segment`

**说明**：用于 Mask.vue 单张图像分割，兼容现有 YOLO 检测服务返回结构。

```http
POST /segment
Content-Type: multipart/form-data
```

**请求参数**：

| 参数名 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `file` | File | 是 | 图片文件 (jpg, png, bmp) |
| `pt_path` | String | 是 | 模型文件路径 (.pt / .pth / .engine) |
| `conf` | Float | 否 | 概率阈值，默认 0.25 |
| `scale` | Float | 否 | 缩放因子，默认 1.0 |
| `n_channels` | Int | 否 | 输入通道数，默认 3 (RGB) |
| `input_size` | Int | 否 | 输入尺寸，默认 512 |
| `model_format` | String | 否 | 模型格式：pytorch / tensorrt |

**返回结构**：
```json
{
  "code": 0,
  "message": "分割成功",
  "data": {
    "detection": {
      "mode": "segment",
      "tumor_detected": true,
      "tumor_type": "glioma",
      "tumor_type_en": "glioma",
      "tumor_count": 2,
      "confidence": 0.92,
      "image_width": 512,
      "image_height": 512,
      "processing_time_ms": 1250.5,
      "boxes": [],
      "polygons": [
        {
          "id": 1,
          "points": [[100, 100], [150, 100], [150, 150], [100, 150]],
          "normalized_points": [[0.195, 0.195], [0.293, 0.195], [0.293, 0.293], [0.195, 0.293]],
          "point_count": 4,
          "label": "gl",
          "tumor_type": "glioma",
          "confidence": 0.92,
          "area_pixels": 2500,
          "box": {"x1": 100, "y1": 100, "x2": 150, "y2": 150}
        }
      ]
    },
    "result_image_base64": "base64_string...",
    "mask_image_base64": "base64_string...",
    "overlay_image_base64": "base64_string...",
    "result_image_path": "results/segment_20240115_103000_test.jpg",
    "original_filename": "test.jpg",
    "model_path": "D:/models/unet/mask_model.pth",
    "conf_threshold": 0.25
  }
}
```

**Mask 类别映射**：
```python
MASK_LABEL_MAP = {
    0: {"name": "background", "cn": "背景", "abbr": "-"},
    1: {"name": "glioma", "cn": "胶质瘤", "abbr": "gl"},
    2: {"name": "meningioma", "cn": "脑膜瘤", "abbr": "me"},
    3: {"name": "pituitary", "cn": "垂体瘤", "abbr": "pi"}
}
```

### 3.3 病例批量分割 `/segment/batch`

**说明**：用于 Multi.vue 病例批量分割，异步处理。

```http
POST /segment/batch
Content-Type: application/json
```

**请求参数**：

| 参数名 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `folderPath` | String | 是 | 输入文件夹路径 |
| `outputPath` | String | 否 | 输出文件夹路径，默认自动生成 |
| `pt_path` | String | 是 | Multi 模型文件路径 |
| `conf` | Float | 否 | 概率阈值，默认 0.25 |
| `n_channels` | Int | 否 | 输入通道数，默认 3 |
| `input_size` | Int | 否 | 输入尺寸，默认 240 |
| `userId` | Int | 否 | 用户 ID |

**返回结构**：
```json
{
  "code": 0,
  "message": "批量分割任务已启动",
  "data": {
    "jobId": "uuid-string",
    "status": "processing",
    "totalFiles": 155,
    "outputPath": "D:/output/case001",
    "folderPath": "D:/data/case001"
  }
}
```

### 3.4 进度查询 `/segment/progress/<jobId>`

```http
GET /segment/progress/<jobId>
```

**返回结构**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "jobId": "uuid-string",
    "status": "completed",
    "progress": 100,
    "totalFiles": 155,
    "processedFiles": 155,
    "currentFile": "slice_154.jpg",
    "elapsedSeconds": 45,
    "outputPath": "D:/output/case001",
    "errorMessage": "",
    "results": [
      {
        "index": 0,
        "fileName": "slice_000.jpg",
        "status": "completed",
        "labels": [0, 1, 2],
        "previewBase64": "base64...",
        "mode": "multi",
        "errorMessage": ""
      }
    ]
  }
}
```

**状态说明**：`idle`, `pending`, `processing`, `completed`, `failed`, `stopped`

### 3.5 停止批量任务 `/segment/stop`

```http
POST /segment/stop
Content-Type: application/json
```

**请求参数**：
```json
{
  "jobId": "uuid-string"
}
```

**返回结构**：
```json
{
  "code": 0,
  "message": "任务已停止",
  "data": {
    "jobId": "uuid-string",
    "status": "stopped"
  }
}
```

### 3.6 路径验证 `/segment/validatePath`

```http
POST /segment/validatePath
Content-Type: application/json
```

**请求参数**：
```json
{
  "path": "D:/data/case001"
}
```

**返回结构**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "path": "D:/data/case001",
    "exists": true,
    "directory": true,
    "totalFiles": 155,
    "imageFiles": ["slice_000.jpg", "slice_001.jpg", ...]
  }
}
```

### 3.7 内存清理 `/memory/clear`（Jetson 专用）

```http
POST /memory/clear
```

**说明**：手动触发 GPU 缓存清理，Jetson 内存紧张时调用。

**返回结构**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "gpu_memory_before": "3072MB",
    "gpu_memory_after": "1024MB"
  }
}
```

## 4. 核心逻辑实现

### 4.1 模型加载（支持 PyTorch + TensorRT）

```python
import torch
import tensorrt as trt
import pycuda.driver as cuda
import pycuda.autoinit

class ModelLoader:
    def __init__(self, device='cuda'):
        self.device = device
        self.current_model = None
        self.current_model_path = None
        self.model_format = None
        
    def load_model(self, model_path, n_channels=3, n_classes=4, 
                   model_format='pytorch', precision='fp32'):
        """加载模型，支持 PyTorch 和 TensorRT 格式"""
        
        # 如果已有模型，先卸载释放内存
        self.unload_model()
        
        if model_format == 'tensorrt':
            return self._load_tensorrt(model_path)
        else:
            return self._load_pytorch(model_path, n_channels, n_classes)
    
    def _load_pytorch(self, model_path, n_channels, n_classes):
        """加载 PyTorch 模型"""
        from unet import UNet
        
        model = UNet(n_channels=n_channels, n_classes=n_classes, bilinear=True)
        state_dict = torch.load(model_path, map_location=self.device)
        
        if 'mask_values' in state_dict:
            mask_values = state_dict.pop('mask_values')
        else:
            mask_values = list(range(n_classes))
            
        model.load_state_dict(state_dict)
        model.to(self.device)
        model.eval()
        
        self.current_model = model
        self.current_model_path = model_path
        self.model_format = 'pytorch'
        
        return model, mask_values
    
    def _load_tensorrt(self, engine_path):
        """加载 TensorRT Engine"""
        with open(engine_path, 'rb') as f:
            runtime = trt.Runtime(trt.Logger())
            engine = runtime.deserialize_cuda_engine(f.read())
            context = engine.create_execution_context()
        
        self.current_model = {'engine': engine, 'context': context}
        self.current_model_path = engine_path
        self.model_format = 'tensorrt'
        
        return self.current_model, list(range(4))  # 默认4类
    
    def unload_model(self):
        """卸载模型释放内存"""
        if self.current_model is not None:
            if self.model_format == 'pytorch':
                del self.current_model
                torch.cuda.empty_cache()
            elif self.model_format == 'tensorrt':
                del self.current_model
            self.current_model = None
            self.current_model_path = None
```

### 4.2 单张图像分割流程

```python
def predict_single_image(image, model_loader, conf=0.25, 
                         input_size=512, n_channels=3):
    """
    单张图像分割流程
    
    步骤：
    1. 图像预处理（Resize → Normalize → Tensor）
    2. 模型推理（PyTorch 或 TensorRT）
    3. 应用概率阈值
    4. 上采样到原图尺寸
    5. 使用 OpenCV findContours 提取多边形
    6. 生成可视化图像
    """
    import cv2
    import numpy as np
    from PIL import Image, ImageDraw
    
    orig_size = image.size
    device = model_loader.device
    model = model_loader.current_model
    model_format = model_loader.model_format
    
    # 1. 预处理
    img_resized = image.resize((input_size, input_size))
    img_array = np.array(img_resized)
    
    if n_channels == 1 and len(img_array.shape) == 3:
        img_array = cv2.cvtColor(img_array, cv2.COLOR_RGB2GRAY)
        img_array = np.expand_dims(img_array, axis=-1)
    
    # 2. 推理
    if model_format == 'pytorch':
        img_tensor = torch.from_numpy(img_array.transpose(2, 0, 1)).float() / 255.0
        img_tensor = img_tensor.unsqueeze(0).to(device)
        
        with torch.no_grad():
            with torch.cuda.amp.autocast():  # FP16 加速
                output = model(img_tensor)
                output = torch.nn.functional.interpolate(
                    output, orig_size[::-1], mode='bilinear'
                )
                probs = torch.softmax(output, dim=1)
                mask = probs.argmax(dim=1).squeeze().cpu().numpy()
    
    elif model_format == 'tensorrt':
        # TensorRT 推理逻辑
        mask = tensorrt_inference(model, img_array, orig_size)
    
    # 3. 应用概率阈值（过滤低置信度像素）
    if model_format == 'pytorch':
        max_probs = probs.max(dim=1).squeeze().cpu().numpy()
        mask[max_probs < conf] = 0
    
    # 4. 提取多边形
    polygons_data = extract_polygons_from_mask(mask, orig_size, conf)
    
    # 5. 生成可视化图像
    result_image, mask_image, overlay_image = build_segmentation_images(
        image, mask, polygons_data
    )
    
    return mask, polygons_data, result_image, mask_image, overlay_image
```

### 4.3 多边形提取

```python
def extract_polygons_from_mask(mask, orig_size, conf_threshold):
    """从掩码中提取多边形"""
    import cv2
    import numpy as np
    
    polygons_data = []
    
    for label_id in range(1, 4):  # 跳过背景 0
        label_mask = (mask == label_id).astype(np.uint8) * 255
        contours, _ = cv2.findContours(
            label_mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE
        )
        
        for contour in contours:
            if len(contour) < 3:
                continue
            
            # 轮廓简化（减少点数）
            epsilon = 0.005 * cv2.arcLength(contour, True)
            approx = cv2.approxPolyDP(contour, epsilon, True)
            
            points = approx.reshape(-1, 2).tolist()
            normalized_points = [
                [p[0] / orig_size[0], p[1] / orig_size[1]] for p in points
            ]
            
            area = cv2.contourArea(contour)
            x, y, w, h = cv2.boundingRect(contour)
            
            label_info = MASK_LABEL_MAP[label_id]
            polygons_data.append({
                "id": len(polygons_data) + 1,
                "points": points,
                "normalized_points": normalized_points,
                "point_count": len(points),
                "label": label_info["abbr"],
                "tumor_type": label_info["name"],
                "confidence": round(conf_threshold, 4),
                "area_pixels": round(area, 2),
                "box": {"x1": x, "y1": y, "x2": x + w, "y2": y + h}
            })
    
    return polygons_data
```

### 4.4 批量分割流程（Jetson 优化版）

```python
def process_batch_segmentation(folder_path, output_path, model_loader,
                                input_size=240, n_channels=3, job_id=None):
    """
    批量分割流程（Jetson 优化）
    
    优化点：
    1. 串行处理避免 OOM
    2. 定期清理 GPU 缓存
    3. 逐张处理，不缓存所有结果
    """
    import os
    from pathlib import Path
    
    image_exts = {'.jpg', '.jpeg', '.png', '.bmp', '.tif', '.tiff'}
    input_files = sorted([f for f in Path(folder_path).iterdir() 
                          if f.suffix.lower() in image_exts])
    
    os.makedirs(output_path, exist_ok=True)
    os.makedirs(os.path.join(output_path, 'images'), exist_ok=True)
    os.makedirs(os.path.join(output_path, 'masks'), exist_ok=True)
    
    slices_metadata = []
    results = []
    
    for idx, img_path in enumerate(input_files):
        try:
            # 处理单张
            image = Image.open(img_path).convert('RGB' if n_channels == 3 else 'L')
            mask, _, _, _, _ = predict_single_image(
                image, model_loader, input_size=input_size, n_channels=n_channels
            )
            
            # 保存
            img_array = np.array(image)
            img_npy_path = os.path.join(output_path, 'images', f'slice_{idx:03d}.npy')
            np.save(img_npy_path, img_array.astype(np.float32))
            
            mask_npy_path = os.path.join(output_path, 'masks', f'slice_{idx:03d}.npy')
            np.save(mask_npy_path, mask.astype(np.uint8))
            
            slices_metadata.append({
                "image": f"images/slice_{idx:03d}.npy",
                "mask": f"masks/slice_{idx:03d}.npy",
                "original": img_path.name
            })
            
            unique_labels = sorted([int(v) for v in np.unique(mask) if v > 0])
            results.append({
                "index": idx,
                "fileName": img_path.name,
                "status": "completed",
                "labels": unique_labels,
                "mode": "multi",
                "errorMessage": ""
            })
            
        except Exception as e:
            results.append({
                "index": idx,
                "fileName": img_path.name,
                "status": "failed",
                "labels": [],
                "mode": "multi",
                "errorMessage": str(e)
            })
        
        # Jetson 优化：定期清理缓存
        if idx % 10 == 0:
            torch.cuda.empty_cache()
        
        # 更新进度
        update_job_progress(job_id, idx + 1, len(input_files))
    
    # 生成 metadata.json
    metadata = {
        "case_id": Path(folder_path).name,
        "slices": slices_metadata,
        "modalities": ["t1c", "t1n", "t2f", "t2w"],
        "axis": 2,
        "shape": [input_size, input_size, len(input_files)],
        "spacing": [1.0, 1.0, 1.0],
        "label_map": {
            "1": "坏死肿瘤核心",
            "2": "瘤周水肿",
            "3": "增强肿瘤"
        }
    }
    
    import json
    with open(os.path.join(output_path, 'metadata.json'), 'w', encoding='utf-8') as f:
        json.dump(metadata, f, ensure_ascii=False, indent=2)
    
    # 最终清理
    torch.cuda.empty_cache()
    
    return results
```

### 4.5 可视化图像生成

```python
def build_segmentation_images(image, mask, polygons_data):
    """生成分割可视化图像"""
    from PIL import Image, ImageDraw
    
    LABEL_COLORS = {
        1: (220, 60, 60),    # gl - 红色
        2: (60, 220, 60),    # me - 绿色
        3: (60, 60, 220)     # pi - 蓝色
    }
    SEGMENT_FILL_ALPHA = 96
    
    result_pil = image.copy().convert('RGB')
    result_draw = ImageDraw.Draw(result_pil)
    
    mask_pil = Image.new('L', image.size, 0)
    mask_draw = ImageDraw.Draw(mask_pil)
    
    overlay_layer = Image.new('RGBA', image.size, (0, 0, 0, 0))
    overlay_draw = ImageDraw.Draw(overlay_layer)
    
    for polygon in polygons_data:
        points = [(int(p[0]), int(p[1])) for p in polygon['points']]
        if len(points) < 3:
            continue
        
        label_id = [k for k, v in MASK_LABEL_MAP.items() 
                   if v['name'] == polygon['tumor_type']][0]
        color = LABEL_COLORS.get(label_id, (128, 128, 128))
        
        mask_draw.polygon(points, fill=255)
        overlay_draw.polygon(points, fill=(*color, SEGMENT_FILL_ALPHA))
        overlay_draw.line(points + [points[0]], fill=(*color, 230), width=3)
        result_draw.line(points + [points[0]], fill=color, width=3)
        
        label_text = f"{polygon['tumor_type']} {polygon['confidence']:.2f}"
        top_point = min(points, key=lambda p: p[1])
        draw_label(result_draw, top_point[0], top_point[1], label_text, color)
    
    overlay_pil = Image.alpha_composite(
        image.convert('RGBA'), overlay_layer
    ).convert('RGB')
    
    return result_pil, mask_pil, overlay_pil
```

## 5. Jetson 内存管理

### 5.1 内存管理器

```python
class JetsonMemoryManager:
    """Jetson Orin Nano 8GB 内存管理器"""
    
    def __init__(self, max_gpu_memory_mb=4096):
        # Orin Nano 8GB: 分配 4GB 给 GPU，保留 4GB 给系统
        self.max_gpu_memory = max_gpu_memory_mb * 1024 * 1024
        self.model_loader = None
        
    def set_model_loader(self, model_loader):
        self.model_loader = model_loader
    
    def check_memory(self):
        """检查 GPU 内存使用"""
        if torch.cuda.is_available():
            allocated = torch.cuda.memory_allocated()
            reserved = torch.cuda.memory_reserved()
            return {
                'allocated_mb': allocated / 1024 / 1024,
                'reserved_mb': reserved / 1024 / 1024,
                'max_mb': self.max_gpu_memory / 1024 / 1024
            }
        return None
    
    def clear_cache(self):
        """清理 GPU 缓存"""
        if torch.cuda.is_available():
            torch.cuda.empty_cache()
            return True
        return False
    
    def should_clear_cache(self):
        """判断是否需要清理缓存"""
        mem_info = self.check_memory()
        if mem_info and mem_info['allocated_mb'] > self.max_gpu_memory / 1024 / 1024 * 0.8:
            return True
        return False
```

### 5.2 配置文件（Orin Nano 8GB 专用）

```yaml
server:
  port: 3408
  host: 127.0.0.1  # Jetson 上只监听本地
  debug: false

model:
  format: tensorrt  # pytorch / tensorrt（推荐TensorRT）
  precision: fp16   # 强制 FP16，节省50%显存
  max_batch_size: 1
  cache_models: 1   # 只缓存1个模型

memory:
  max_gpu_memory_mb: 4096  # 8GB 总内存，分配 4GB 给 GPU
  clear_cache_interval: 5  # 每处理5张清理（8GB版本需更频繁）
  enable_memory_tracking: true

optimization:
  enable_amp: true        # 自动混合精度
  num_workers: 0          # Jetson 上设为0避免多进程问题
  pin_memory: false

logging:
  level: INFO
  file: logs/unet-service.log
```

## 6. 前端集成说明

### 6.1 双环境配置

```javascript
// config.js
const config = {
  // PC 开发环境
  development: {
    // Mask.vue 直连 UNet（开发调试方便）
    unetServiceUrl: 'http://localhost:3408',
    maskDirectCall: true,
    
    // SpringBoot 后端
    apiBaseUrl: 'http://localhost:9527'
  },
  
  // Jetson 生产环境
  production: {
    // 统一走 SpringBoot 转发
    unetServiceUrl: null,
    maskDirectCall: false,
    
    // 只暴露 9527
    apiBaseUrl: 'http://jetson-ip:9527'
  }
}
```

### 6.2 Mask.vue 调用逻辑

```javascript
// 根据环境决定调用方式
async function segmentImage(file, modelPath, params) {
  if (config.maskDirectCall) {
    // PC：直连 UNet 服务
    const formData = new FormData()
    formData.append('file', file)
    formData.append('pt_path', modelPath)
    formData.append('conf', params.conf)
    formData.append('input_size', params.inputSize)
    
    return axios.post(`${config.unetServiceUrl}/segment`, formData)
  } else {
    // Jetson：走 SpringBoot 转发
    return request.post('/segment', {
      file: file,
      ptPath: modelPath,
      conf: params.conf,
      inputSize: params.inputSize
    })
  }
}
```

## 7. 数据库扩展脚本

```sql
-- 扩展 detect_record 表
ALTER TABLE detect_record ADD COLUMN (
  `segment_type` VARCHAR(20) DEFAULT NULL COMMENT '分割类型: single(单图分割), batch(病例分割)',
  `case_path` VARCHAR(500) DEFAULT NULL COMMENT '病例路径(批量分割时)',
  `output_path` VARCHAR(500) DEFAULT NULL COMMENT '输出路径',
  `total_slices` INT DEFAULT NULL COMMENT '总切片数(批量分割时)',
  `service_type` VARCHAR(20) DEFAULT 'yolo' COMMENT '服务类型: yolo, unet-mask, unet-multi',
  `input_size` INT DEFAULT NULL COMMENT '输入尺寸',
  `model_format` VARCHAR(20) DEFAULT 'pytorch' COMMENT '模型格式: pytorch, tensorrt'
);

-- 创建 UNet 模型配置表
CREATE TABLE IF NOT EXISTS `unet_model_config` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `model_name` VARCHAR(100) NOT NULL COMMENT '模型名称',
  `model_path` VARCHAR(500) NOT NULL COMMENT '模型文件路径',
  `model_type` VARCHAR(20) NOT NULL COMMENT '模型类型: mask, multi',
  `model_format` VARCHAR(20) DEFAULT 'pytorch' COMMENT '模型格式: pytorch, tensorrt, onnx',
  `precision` VARCHAR(10) DEFAULT 'fp32' COMMENT '推理精度: fp32, fp16, int8',
  `n_channels` INT DEFAULT 3 COMMENT '输入通道数',
  `n_classes` INT DEFAULT 4 COMMENT '输出类别数',
  `input_size` INT DEFAULT 512 COMMENT '输入尺寸',
  `is_default` TINYINT(1) DEFAULT 0 COMMENT '是否为默认模型',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_model_type` (`model_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='UNet模型配置表';
```

## 8. 部署检查清单

### 8.1 PC 开发环境

- [ ] Python 3.8+ 安装
- [ ] PyTorch (CUDA 版本) 安装
- [ ] Flask、Flask-CORS 安装
- [ ] OpenCV-Python 安装
- [ ] UNet 模型文件准备 (.pth)
- [ ] 端口 3408 未被占用

### 8.2 Jetson 生产环境

- [ ] JetPack 安装（包含 CUDA、cuDNN、TensorRT）
- [ ] Jetson 专用 PyTorch 安装
- [ ] TensorRT Python API 可用
- [ ] UNet 模型转换为 TensorRT engine
- [ ] 配置文件 config-jetson.yml 准备
- [ ] 服务监听 127.0.0.1（不暴露外网）
- [ ] SpringBoot 转发配置完成
- [ ] 日志目录创建

## 9. 性能参考

### 9.1 PC 环境（RTX 3060）

| 输入尺寸 | PyTorch FP32 | TensorRT FP16 |
|---------|-------------|---------------|
| 256×256 | 50ms | 20ms |
| 512×512 | 120ms | 45ms |
| 1024×1024 | 450ms | 180ms |

### 9.2 Jetson Orin Nano Super 8GB 性能实测

| 输入尺寸 | PyTorch FP32 | TensorRT FP16 | 显存占用 |
|---------|-------------|---------------|---------|
| 128×128 | 45ms | 18ms | ~1.2GB |
| 240×240 | 150ms | 55ms | ~1.8GB |
| 256×256 | 180ms | 65ms | ~2.0GB |
| 512×512 | 750ms | 280ms | ~3.5GB |

**Orin Nano 8GB 专用建议**：
- **推荐尺寸**：240×240（平衡速度和精度）
- **最大尺寸**：512×512（再大可能 OOM）
- **强制 FP16**：可节省 50% 显存，速度提升 2.5-3x
- **内存监控**：预留 3-4GB 给系统，GPU 使用不超过 4GB
