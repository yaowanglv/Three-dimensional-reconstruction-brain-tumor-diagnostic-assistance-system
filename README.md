# 三维重建脑肿瘤辅助诊断系统

仓库：https://github.com/yaowanglv/Three-dimensional-reconstruction-brain-tumor-diagnostic-assistance-system.git

基于大模型的脑肿瘤辅助诊断与分析系统：YOLO 检测 / 分割、UNet 切片分割、网格三维重建，以及 Spring Boot + Vue 业务端。

## 技术栈与端口

| 模块 | 技术 | 端口 |
|------|------|------|
| 前端 | Vue 3 + Element Plus + Vite | 4000（开发） |
| 后端 | Spring Boot 3 + MyBatis + MySQL | 9527 |
| YOLO 检测 | Flask + 定制 Ultralytics | 5001 |
| UNet 分割 | Flask + PyTorch | 3408 |
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

## 不会进 Git 的内容

`node_modules/`、`target/`、`dist/`、`uploads/`、`.idea/`、`.env`、`application-local.yml`、`docs/`、`sh/`、运行日志。UNet `.pth` 走 Git LFS；YOLO `.pt` / `.onnx` 直接提交。

## Python 依赖说明

`detect_service/requirements.txt` 按推理 import 筛选。`detect_service/ultralytics` 是定制 fork，安装官方 `ultralytics` 会盖掉本地包。
