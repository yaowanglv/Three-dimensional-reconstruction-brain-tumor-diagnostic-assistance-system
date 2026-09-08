# UNet 分割接口需求文档 (PRD)

## 1. 项目背景

为了增强系统的脑肿瘤分割能力，我们需要引入基于 PyTorch 的 UNet 模型。该模型将作为一个独立的 Python 微服务运行在端口 3408，与现有的 YOLO 检测服务（端口 5001）和 Mesh 服务（端口 5002）协同工作。

UNet 服务将支持两个不同的模型：
- **Mask 模型**：用于单张图像分割（Mask.vue），输出肿瘤类型分类
- **Multi 模型**：用于病例批量分割（Multi.vue），输出 BraTS 标准的多类别分割

### 1.1 Jetson 部署目标

本系统计划部署到 NVIDIA Jetson 边缘计算设备，需考虑以下约束：
- **内存有限**：4-16GB 内存，需优化服务内存占用
- **ARM 架构**：PyTorch 需使用 Jetson 专用版本
- **显存共享**：GPU 显存与系统内存共享，需控制模型加载
- **TensorRT 支持**：模型可优化为 TensorRT engine 提升推理速度
- **端口精简**：生产环境只暴露 9527 端口，其他服务监听 localhost

## 2. 功能需求

### 2.1 单张图像分割 (适配 Mask.vue)

- **用户故事**：作为一名医生，我希望上传一张 MRI 图片，系统能快速识别并标记出肿瘤区域（胶质瘤、脑膜瘤、垂体瘤），同时给出诊断建议。
- **功能点**：
  - 支持上传常见图片格式 (jpg, png, bmp)。
  - 返回分割后的可视化结果（原图叠加、纯掩码、半透明叠加）。
  - 返回检测到的肿瘤类型、置信度、多边形等元数据。
  - 结果需与现有的 AI 分析流程兼容（保持与 detect-api.py 一致的返回结构）。
  - 兼容 YOLO 检测服务和 UNet 分割服务，通过配置切换。
  - 支持通过 Config.vue 配置模型路径（.pt 或 .pth 文件）。
  - 支持在 Mask.vue 页面下拉选择输入尺寸（默认 512×512，可配置）。
  - **Jetson 优化**：支持 TensorRT 模型格式（.engine），FP16 推理

### 2.2 病例批量分割 (适配 Multi.vue)

- **用户故事**：作为一名医生，我希望能选择一个包含多张切片的病例文件夹，系统自动进行批量分割，以便进行 3D 重建分析。
- **功能点**：
  - 支持文件夹上传或指定服务器路径。
  - 实时显示分割进度。
  - 分割结果（掩码）需保存为 `.npy` 格式，并保持与原始切片对应的目录结构。
  - 支持的切片语义（Multi 模式 - BraTS 标准）：`0:背景, 1:坏死核心, 2:水肿, 3:增强肿瘤`。
  - 生成符合 mesh-api 规范的目录结构（`images/` 和 `masks/` + `metadata.json`）。
  - 原始切片（jpg/png）自动转换为 `.npy` 格式存入 images/ 目录。
  - 支持在 Multi.vue 页面下拉选择输入尺寸（默认 240×240，可配置）。
  - **Jetson 优化**：串行处理避免 OOM，定期清理 GPU 缓存

### 2.3 3D 重建兼容性

- **用户故事**：作为一名医生，在完成病例分割后，我希望直接点击查看 3D 模型，直观观察肿瘤的立体位置和形态。
- **功能点**：
  - 批量分割的输出目录结构必须符合 `mesh-api` 的读取规范（`images/` 和 `masks/` + `metadata.json`）。
  - 掩码文件的数值类型和形状必须能被 `mesh-api` 正确解析。
  - UNet 服务与 Mesh 服务通过 3408 端口交换数据。

### 2.4 历史记录查看 (适配 History.vue)

- **用户故事**：我希望能查看过去的分割记录，并能快速跳转到当时的 3D 视图或详情页。
- **功能点**：
  - 分割任务完成后，需在数据库中生成一条记录。
  - 记录需区分"单图分割"和"病例分割"两种类型。
  - 记录需包含：执行时间、执行人、状态、结果缩略图、输出路径、分割类型。
  - 批量分割记录点击"查看"时，能跳转到 Threedim.vue 根据保存的数据路径加载并渲染 3D 模型。
  - 单张分割记录点击"查看"时，跳转到 Mask.vue 查看详情。

### 2.5 调用方式（Jetson 优化）

| 环境 | 调用方式 | 说明 |
|------|---------|------|
| PC 开发 | Mask.vue 直连 `localhost:3408/segment` | 开发效率高，调试方便 |
| Jetson 生产 | 统一走 `localhost:9527` (SpringBoot 转发) | 内存优化，端口精简，安全 |

**Jetson 部署架构：**
```
Jetson Device
├─ SpringBoot (端口 9527) - 统一入口，对外暴露
│   ├─ 接收所有前端请求
│   ├─ 按需加载/卸载模型（内存优化）
│   └─ 转发到本地 UNet/Mesh
│
├─ UNet Service (端口 3408, localhost only)
│   └─ 单张/批量分割
│
└─ Mesh Service (端口 5002, localhost only)
    └─ 3D 重建
```

## 3. 数据规范

### 3.1 类别标签 (Mask 模式 - 单张分割)

| 索引 | 缩写 | 中文名称 | 英文名称 |
| :--- | :--- | :--- | :--- |
| 0 | - | 背景 | background |
| 1 | gl | 胶质瘤 | glioma |
| 2 | me | 脑膜瘤 | meningioma |
| 3 | pi | 垂体瘤 | pituitary |

**说明**：Mask 模式使用专门的 Mask 模型，输出 4 个类别。

### 3.2 类别标签 (Multi 模式 - BraTS 标准)

| 索引 | 中文名称 | 英文名称 |
| :--- | :--- | :--- |
| 0 | 背景 | background |
| 1 | 坏死肿瘤核心 | Necrotic tumor core |
| 2 | 瘤周水肿 | Peritumoral edema |
| 3 | 增强肿瘤 | Enhancing tumor |

**说明**：Multi 模式使用专门的 Multi 模型，输出 4 个类别。

### 3.3 输出目录结构 (批量分割)

```
outputPath/
├── metadata.json          # 包含切片列表、模态信息、轴方向等
├── images/                # 原始图像切片（.npy 格式）
│   ├── slice_000.npy     # 形状 (H, W), dtype=float32
│   ├── slice_001.npy
│   └── ...
└── masks/                 # 分割掩码（.npy 格式）
    ├── slice_000.npy     # 形状 (H, W), dtype=uint8, 值域 0-3
    ├── slice_001.npy
    └── ...
```

**metadata.json 示例**：
```json
{
  "case_id": "case001",
  "slices": [
    {"image": "images/slice_000.npy", "mask": "masks/slice_000.npy"},
    {"image": "images/slice_001.npy", "mask": "masks/slice_001.npy"}
  ],
  "modalities": ["t1c", "t1n", "t2f", "t2w"],
  "axis": 2,
  "shape": [240, 240, 155],
  "spacing": [1.0, 1.0, 1.0],
  "label_map": {
    "1": "坏死肿瘤核心",
    "2": "瘤周水肿",
    "3": "增强肿瘤"
  }
}
```

### 3.4 输入尺寸配置

| 模式 | 默认值 | 可选尺寸 | 配置位置 |
| :--- | :--- | :--- | :--- |
| Mask (单张) | 512×512 | 256×256, 512×512, 640×640, 1024×1024 | Config.vue 设置默认值，Mask.vue 页面下拉选择 |
| Multi (批量) | 240×240 | 128×128, 240×240, 256×256, 512×512 | Config.vue 设置默认值，Multi.vue 页面下拉选择 |

**说明**：默认值在选项右侧显示"默认值"标识。Jetson 上可适当降低尺寸提升速度。

### 3.5 模型输入通道配置

| 配置项 | 默认值 | 可选值 | 配置位置 |
| :--- | :--- | :--- | :--- |
| 输入通道数 | 3 (RGB) | 1 (灰度), 3 (RGB) | Config.vue 界面配置 |

### 3.6 模型格式配置（Jetson 扩展）

| 配置项 | PC 环境 | Jetson 环境 | 配置位置 |
| :--- | :--- | :--- | :--- |
| 模型格式 | pytorch (.pth) | tensorrt (.engine) | Config.vue 模型导入时选择 |
| 推理精度 | FP32 | FP16 / INT8 | Config.vue 配置 |

## 4. 非功能需求

- **性能**：单张图片分割响应时间应小于 3 秒（GPU 环境，512×512 输入）。
- **并发**：服务需支持多用户同时访问，批量任务需异步处理。
- **端口**：UNet 服务运行端口固定为 `3408`，Jetson 上监听 `127.0.0.1`。
- **兼容性**：
  - 支持 .pt 和 .pth 模型文件格式
  - Mask.vue 兼容 YOLO 检测服务和 UNet 分割服务
  - 与现有 detect-api.py 返回结构保持一致
- **Jetson 优化**：
  - 支持 TensorRT 模型加速
  - 内存管理：限制 GPU 显存使用，定期清理缓存
  - 模型按需加载：避免同时加载多个大模型

## 5. 数据库扩展

### 5.1 扩展 detect_record 表

新增字段：

| 字段名 | 类型 | 说明 |
| :--- | :--- | :--- |
| `segment_type` | VARCHAR(20) | 分割类型：'single' (单图分割), 'batch' (病例分割) |
| `case_path` | VARCHAR(500) | 病例路径（批量分割时有效）|
| `output_path` | VARCHAR(500) | 输出路径 |
| `total_slices` | INT | 总切片数（批量分割时有效）|
| `service_type` | VARCHAR(20) | 服务类型：'yolo', 'unet-mask', 'unet-multi' |
| `input_size` | INT | 输入尺寸 |

### 5.2 新增 unet_model_config 表

用于存储 UNet 模型配置：

| 字段名 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | INT | 主键 |
| `model_name` | VARCHAR(100) | 模型名称 |
| `model_path` | VARCHAR(500) | 模型文件路径 |
| `model_type` | VARCHAR(20) | 模型类型：'mask', 'multi' |
| `model_format` | VARCHAR(20) | 模型格式：'pytorch', 'tensorrt', 'onnx' |
| `precision` | VARCHAR(10) | 推理精度：'fp32', 'fp16', 'int8' |
| `n_channels` | INT | 输入通道数 |
| `n_classes` | INT | 输出类别数 |
| `input_size` | INT | 输入尺寸 |
| `is_default` | BOOLEAN | 是否为默认模型 |

## 6. 服务架构

### 6.1 PC 开发环境

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   Mask.vue      │────▶│  SpringBoot     │◀────│   Multi.vue     │
│  (单张分割)      │     │   (端口 9527)    │     │  (批量分割)      │
└────────┬────────┘     └────────┬────────┘     └─────────────────┘
         │                       │
         │ (直接调用)            │ (转发)
         ▼                       ▼
┌─────────────────┐      ┌─────────────────┐
│  UNet Service   │      │  Mesh Service   │
│   (端口 3408)    │      │   (端口 5002)    │
└─────────────────┘      └─────────────────┘
```

### 6.2 Jetson 生产环境

```
┌─────────────────────────────────────────────────────────────┐
│                         前端                                  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │  Mask.vue   │  │  Multi.vue  │  │    History.vue      │  │
│  └──────┬──────┘  └──────┬──────┘  └─────────────────────┘  │
└─────────┼────────────────┼──────────────────────────────────┘
          │                │
          └────────────────┘
                   │
                   ▼ (统一入口)
          ┌─────────────────┐
          │   SpringBoot    │
          │   (端口 9527)    │
          │  对外暴露，转发   │
          └────────┬────────┘
                   │
      ┌────────────┼────────────┐
      ▼            ▼            ▼
┌──────────┐ ┌──────────┐ ┌──────────┐
│   UNet   │ │   Mesh   │ │   YOLO   │
│  3408    │ │  5002    │ │  5001    │
│ localhost│ │ localhost│ │ localhost│
└──────────┘ └──────────┘ └──────────┘
```

## 7. 页面路由与跳转

| 页面 | 功能 | 跳转目标 |
| :--- | :--- | :--- |
| Mask.vue | 单张分割 | 分割完成后显示结果，支持导出报告 |
| Multi.vue | 批量分割 | 分割完成后点击"跳转到3D可视化"进入 Threedim.vue |
| Threedim.vue | 3D 可视化 | 从 Multi.vue 或 History.vue 进入 |
| History.vue | 历史记录 | 单图分割 → Mask.vue, 病例分割 → Threedim.vue |

## 8. Jetson 部署优化清单

### 8.1 模型优化

| 优化项 | PC 环境 | Jetson 环境 | 收益 |
|--------|---------|-------------|------|
| 模型格式 | .pth (PyTorch) | .engine (TensorRT) | 速度提升 3-5x |
| 推理精度 | FP32 | FP16 | 速度提升 2x，显存减半 |
| 批量推理 | 并行 | 串行 | 避免 OOM |
| 输入尺寸 | 512×512 | 可动态降低 | 减少计算量 |

### 8.2 内存管理策略（Orin Nano 8GB 专用）

- **模型缓存**：限制只缓存 1 个模型，切换时卸载旧模型
- **GPU 内存上限**：设置 4GB（4096MB）上限，保留 4GB 给系统和其他服务
- **定期清理**：每处理 5 张图像调用 `torch.cuda.empty_cache()`
- **半精度推理**：强制使用 FP16，减少 50% 显存占用
- **批量处理**：逐张处理，避免同时加载多张图像
- **输入尺寸建议**：默认 240×240，最大不超过 512×512

### 8.3 配置文件（Jetson 专用）

```yaml
# config-jetson.yml
server:
  port: 3408
  host: 127.0.0.1  # 只监听本地

model:
  format: tensorrt  # pytorch / tensorrt
  precision: fp16   # fp32 / fp16
  max_batch_size: 1

memory:
  limit_gpu_memory: 2048  # MB
  clear_cache_interval: 10

optimization:
  enable_amp: true
  max_concurrent_jobs: 1  # Jetson 上串行
```
