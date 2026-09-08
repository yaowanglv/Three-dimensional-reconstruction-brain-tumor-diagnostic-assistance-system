# 三维重建脑肿瘤辅助诊断系统

仓库：https://github.com/yaowanglv/Three-dimensional-reconstruction-brain-tumor-diagnostic-assistance-system.git

基于大模型的脑肿瘤辅助诊断与分析系统：YOLO 检测 / 分割、UNet 切片分割、网格三维重建，以及 Spring Boot + Vue 业务端。

## 技术栈与端口

| 模块 | 技术 | 端口 |
|------|------|------|
| 前端 | Vue 3 + Element Plus + Vite | 4000（开发） |
| 后端 | Spring Boot 3 + MyBatis + MySQL | 9527 |
| YOLO 检测 | Flask + 定制 Ultralytics | 5001 |
| UNet 分割 | Flask + PyTorch（训练基于 [milesial/Pytorch-UNet](https://github.com/milesial/Pytorch-UNet)） | 3408 |
| Mesh 重建 | Flask + NumPy/SciPy | 5002 |

## 目录结构

```
vue/                 前端
springb/             Java 后端
detect_service/      Python 推理服务（detect-api / unet4engine / mesh-api）
models/              业务权重（YOLO 框/mask，UNet .pth）
sql/                 仅结构的建库脚本
mcp/mysql/           可选 MySQL MCP
```

## 环境要求

- JDK 17、Maven
- Node.js 18+（前端）
- Python 3.10+（推理）
- MySQL 8
- Git LFS（拉取 `models/unet/brats/*.pth`）

## 建库

见 [sql/README.md](sql/README.md)，按 `00` → `05` 顺序执行。默认库名 `dsecond`。

## 配置（不要把密钥提交进 Git）

```bash
copy springb\src\main\resources\application-local.yml.example springb\src\main\resources\application-local.yml
```

至少填写：

- MySQL 账号密码
- `jwt.secret`（Base64，解码后 ≥ 32 字节）
- 可选：DeepSeek / GLM / Kimi API Key、飞书应用凭证

`application.yml` 只含占位符和环境变量。

系统配置页扫描模型目录时，指向本仓库：

- 检测：`models/yolo/detect`
- 分割：`models/yolo/mask`
- 批量 / 三维：`models/unet/brats`

## 启动顺序

1. MySQL 已建库
2. `detect_service`：`start_detect.bat`、`start_unet.bat`、`start_mesh.bat`
3. `cd springb && mvn spring-boot:run`
4. `cd vue && npm install && npm run dev`

登录后可在「系统配置」登记模型路径。首次需自行向 `admin` 表插入账号。

## 数据集下载与存放

数据集不进 Git。网盘分享名：**yds-brain**。

- 链接：https://pan.quark.cn/s/3c318aced0de?pwd=vyjJ
- 提取码：`vyjJ`
- 也可复制：`/~ef683akbqH~:/` 后用夸克 APP 打开

解压后建议按下面两套目录放置，和训练/演示时的本地路径一致。本仓库推理只需要 `models/` 里的权重；下列数据用于复现训练或在前端选病例切片。

### 1. YOLO 检测 / 实例分割：BRISC-2025

对应本地：`D:\algorithms\V11-dmt\dataset\brain\BRISC-2025`

胶质瘤 / 脑膜瘤 / 垂体瘤（`gl` / `me` / `pi`）的检测框与 mask 数据。YOLO 配置文件 `BRISC-2025.yaml` 里的 `path` 指向 `my-BRISC-2025`。

```
BRISC-2025/
├── archive.zip
└── archive/brisc2025/
    ├── BRISC-2025.yaml          # YOLO data yaml（path → my-BRISC-2025）
    ├── README.md
    ├── manifest.csv
    ├── manifest.json
    ├── classification_task/     # 分类：train|test × glioma/meningioma/pituitary/no_tumor
    ├── segmentation_task/       # 分割：train|test × images/labels/masks
    │   └── BRISC-2025-seg.yaml
    └── my-BRISC-2025/           # 实际用于 YOLO 训练的 images/labels 划分
```

在本系统中：`detect_service/detect-api.py`（端口 5001）加载 `models/yolo/detect` 与 `models/yolo/mask` 的权重；BRISC 图像可作检测页、分割页的测试样本。

### 2. UNet 切片 / 三维重建：BraTS 切片

UNet 训练基于 [milesial/Pytorch-UNet](https://github.com/milesial/Pytorch-UNet)。本系统在该仓库上增加了 `unet4engine.py` 等推理接口，以及 BraTS 切片数据和业务权重。

对应本地：`D:\algorithms\Pytorch-UNet-master\data\threedim`

按病例切片导出的 PNG，文件名形如 `BraTS-SSA-xxxxx-xxx_slice_yyy.png`，`imgs` 与 `masks` 一一对应。

```
threedim/
├── datareadme.txt
├── imgs/     # MRI 切片
└── masks/    # 像素标签：0 背景，1 坏死肿瘤核心，2 瘤周水肿，3 增强肿瘤
```

在本系统中：`detect_service/unet4engine.py`（端口 3408）加载 `models/unet/brats/checkpoint_epoch100.pth` 做切片分割；`mesh-api.py`（端口 5002）用分割体积做三维网格。批量分割 / 3D 页选择病例目录时，指向解压后的 `threedim`（或其中按病例整理的子目录）。

### 推荐对照

| 用途 | 网盘内容 | 本地目录 | 本仓库权重 |
|------|----------|----------|------------|
| YOLO 检测框 | BRISC-2025 | `...\dataset\brain\BRISC-2025` | `models/yolo/detect/` |
| YOLO mask | BRISC-2025 `segmentation_task` / `my-BRISC-2025` | 同上 | `models/yolo/mask/` |
| UNet / 三维重建 | threedim（imgs + masks） | `...\data\threedim` | `models/unet/brats/` |

## 不会进 Git 的内容

`node_modules/`、`target/`、`dist/`、`uploads/`、`.idea/`、`.env`、`application-local.yml`、`docs/`、`sh/`、运行日志、数据集。UNet `.pth` 走 Git LFS；YOLO `.pt` / `.onnx` 直接提交。

## Python 依赖说明

`detect_service/requirements.txt` 按推理 import 筛选。`detect_service/ultralytics` 是定制 fork，安装官方 `ultralytics` 会盖掉本地包。
