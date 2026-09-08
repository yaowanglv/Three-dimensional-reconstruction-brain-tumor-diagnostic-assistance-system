# Jetson Orin Nano Super 8GB 三项目部署方案

> 编码要求：本文件按 UTF-8 保存和读取。  
> 项目范围：`D:\YDS\code`、`D:\YDS\blade - 未改UI`、`D:\YDS\skin`。  
> 目标设备：Jetson Orin Nano Super 8GB，已安装 Ubuntu/JetPack。

本文按“先在 PC 做什么，再在 Jetson 做什么”的逻辑写。每一步都标明执行位置、配置文件和产物。

---

## 文档目录

| 章节 | 内容 | 什么时候看 |
|---|---|---|
| [0. 先确认的部署边界](#0-先确认的部署边界) | 三个项目、端口、推荐部署形态 | 开始部署前先看 |
| [1. PC 端准备清单](#1-pc-端准备清单) | 前端构建、后端 jar、数据库 SQL、模型文件、上传产物 | 在 PC 上打包和整理文件时看 |
| [2. Jetson 系统与硬件设置](#2-jetson-系统与硬件设置) | 系统更新、性能模式、swap、目录规划 | 新 Jetson 初始化时看 |
| [3. Jetson 数据库部署](#3-jetson-数据库部署) | MySQL 初始化、Navicat SQL 导入、路径检查 | 配数据库时看 |
| [4. Jetson 后端部署](#4-jetson-后端部署) | Spring Boot jar、`application-prod.yml`、systemd | 部署 Java 后端时看 |
| [5. Jetson 前端与 Nginx 部署](#5-jetson-前端与-nginx-部署) | 前端 dist、Nginx 反代、Config 页面 URL | 部署 Web 页面和反代时看 |
| [6. Jetson Python 模型服务部署](#6-jetson-python-模型服务部署) | YOLO、UNet、Mesh、blade、skin 五个 Python 服务 | 部署检测、分割、Mesh 服务时看 |
| [7. 模型导出 TensorRT engine 与验证](#7-模型导出-tensorrt-engine-与验证) | `.pt/.pth` 在 Jetson 本机导出 `.engine`、验证和 Config 配置 | 处理 `.engine` 时重点看 |
| [8. 算法服务工程化包装与一键启动](#8-算法服务工程化包装与一键启动) | 瘦身包、整包兜底、systemd target、一键启动 | 想减少算法目录依赖或统一启动时看 |
| [9. 最小上线顺序](#9-最小上线顺序) | 从 PC 到 Jetson 的最短上线流程 | 真正执行部署时按此核对 |
| [10. 文件配置总表](#10-文件配置总表) | PC/Jetson 配置文件和 Web Config 项汇总 | 查配置文件位置时看 |
| [11. 已确认决策与保留问题](#11-已确认决策与保留问题) | 按项目启停、两种访问方案、不公网访问、保留 JetPack 问题 | 确认部署策略时看 |
| [12. 官方参考](#12-官方参考) | NVIDIA、Miniforge 等官方资料 | 查官方说明时看 |

重点提醒：

- 不清楚 `.engine` 怎么导出时，直接看第 7 章。
- 不清楚某个服务该在哪台机器配置时，先看第 10 章文件配置总表。
- 只想把项目跑起来时，先按第 9 章执行，再回看第 6、7、11 章处理模型服务和访问方式。

---

## 0. 先确认的部署边界

### 0.1 三个项目与推荐名称

| 项目 | PC 路径 | Jetson 建议路径 | 数据库 | Spring Boot 端口 | 前端端口 |
|---|---|---|---|---:|---:|
| brain 当前项目 | `D:\YDS\code` | `/opt/yds/apps/brain` | `dsecond` | `9527` | `8081` |
| blade 刀具项目 | `D:\YDS\blade - 未改UI` | `/opt/yds/apps/blade` | `blade` | `1234` | `8082` |
| skin 皮肤项目 | `D:\YDS\skin` | `/opt/yds/apps/skin` | `skin` | `1907` | `8083` |

### 0.2 Python 服务端口规划

| 服务 | Jetson 建议路径 | 端口 | 调用方 |
|---|---|---:|---|
| brain YOLO 检测/分割 | `/opt/yds/services/yolo-v11-dmt` | `5001` | brain 后端 |
| brain UNet 分割 | `/opt/yds/services/unet` | `3408` | brain 后端 |
| brain Mesh 三维重建 | `/opt/yds/services/mesh` | `5002` | brain 后端 |
| blade 检测/视频 | `/opt/yds/services/blade-detect` | `6522` | blade 后端 |
| skin 检测/视频 | `/opt/yds/services/skin-detect` | `2026` | skin 后端 |

### 0.3 推荐部署形态

在 Jetson 上：

```text
Nginx
  ├─ 8081 -> brain 前端 dist -> /api 反代到 9527
  ├─ 8082 -> blade 前端 dist -> /api 反代到 1234
  └─ 8083 -> skin  前端 dist -> /api 反代到 1907

Spring Boot
  ├─ brain :9527 -> MySQL dsecond -> Python :5001/:3408/:5002
  ├─ blade :1234 -> MySQL blade   -> Python :6522
  └─ skin  :1907  -> MySQL skin    -> Python :2026

MySQL
  ├─ dsecond
  ├─ blade
  └─ skin
```

---

## 1. PC 端准备清单

本章全部在 **PC 端** 执行。

### 1.1 PC 端需要准备的产物

| 产物 | 生成位置 | 上传到 Jetson |
|---|---|---|
| brain 前端 `dist` | `D:\YDS\code\vue\dist` | `/var/www/yds/brain` |
| blade 前端 `dist` | `D:\YDS\blade - 未改UI\vue\dist` | `/var/www/yds/blade` |
| skin 前端 `dist` | `D:\YDS\skin\vue\dist` | `/var/www/yds/skin` |
| brain 后端 jar | `D:\YDS\code\springb\target\*.jar` | `/opt/yds/apps/brain/brain.jar` |
| blade 后端 jar | `D:\YDS\blade - 未改UI\springb\target\*.jar` | `/opt/yds/apps/blade/blade.jar` |
| skin 后端 jar | `D:\YDS\skin\springb\target\*.jar` | `/opt/yds/apps/skin/skin.jar` |
| 数据库 SQL | Navicat 导出 | `/opt/yds/db/*.sql` |
| 模型文件 | PC 模型目录 | `/opt/yds/models/...` |
| Python 算法服务代码 | 算法项目目录 | `/opt/yds/services/...` |

### 1.2 PC 端构建三个前端

执行位置：PC。

brain：

```powershell
cd /d D:\YDS\code\vue
npm install
npm run build
```

blade：

```powershell
cd /d "D:\YDS\blade - 未改UI\vue"
npm install
npm run build
```

skin：

```powershell
cd /d D:\YDS\skin\vue
npm install
npm run build
```

生成产物：

```text
vue/dist/
```

### 1.3 PC 端构建三个 Spring Boot 后端

执行位置：PC。

brain：

```powershell
cd /d D:\YDS\code\springb
mvn clean package -DskipTests
```

blade：

```powershell
cd /d "D:\YDS\blade - 未改UI\springb"
mvn clean package -DskipTests
```

skin：

```powershell
cd /d D:\YDS\skin\springb
mvn clean package -DskipTests
```

生成产物：

```text
springb/target/springb-0.0.1-SNAPSHOT.jar
```

上传到 Jetson 后建议重命名：

```text
brain.jar
blade.jar
skin.jar
```

### 1.4 PC 端用 Navicat 导出数据库 SQL

执行位置：PC，工具：Navicat。

这是推荐方案。PC 开发完成后，从 Navicat 导出每个数据库，再在 Jetson 导入。

#### 1.4.1 导出“仅结构”

适合空环境部署，不带测试数据。

Navicat 操作：

```text
右键数据库 -> 转储 SQL 文件
  勾选：结构
  不勾选：数据
  字符集：utf8mb4
  DROP/CREATE：按是否覆盖目标库决定
```

推荐文件名：

```text
brain_dsecond_schema.sql
blade_schema.sql
skin_schema.sql
```

#### 1.4.2 导出“结构 + 数据”

适合复刻 PC 开发状态。

Navicat 操作：

```text
右键数据库 -> 转储 SQL 文件
  勾选：结构
  勾选：数据
  字符集：utf8mb4
  建议包含 CREATE DATABASE / USE database
```

推荐文件名：

```text
brain_dsecond_full.sql
blade_full.sql
skin_full.sql
```

注意：

- 如果导出历史检测记录，必须同步 `uploads/` 文件，否则数据库路径存在但图片文件不存在。
- 如果数据中有 Windows 路径，如 `D:\models\xxx.pt`，导入 Jetson 后必须在 Config 页面改成 Linux 路径。
- 生产环境建议不要迁移硬编码 API Key。AI Key、飞书 Key、JWT Secret 应改用环境变量或外部配置。

### 1.5 PC 端整理模型文件

执行位置：PC。

建议模型最终按项目和服务分类：

```text
models/
  brain/
    yolo/
      best.pt
      best.engine
    unet/
      best.pth
      best.engine
  blade/
    best.pt
    best.engine
  skin/
    best.pt
    best.engine
```

上传到 Jetson 后建议：

```text
/opt/yds/models/brain/yolo
/opt/yds/models/brain/unet
/opt/yds/models/blade
/opt/yds/models/skin
```

注意：

- `.engine` 最好在 Jetson 本机导出。
- PC 导出的 `.engine` 不保证能在 Jetson 上用。
- `.pt` 可先上传到 Jetson，用 Jetson 环境导出 `.engine`。
- `.engine` 的 Jetson 本机导出、验证和项目配置方法详见第 7 章。

### 1.6 PC 端上传文件到 Jetson

执行位置：PC。

示例：

```powershell
scp -r D:\YDS\code\vue\dist jetson:/tmp/brain-dist
scp D:\YDS\code\springb\target\springb-0.0.1-SNAPSHOT.jar jetson:/tmp/brain.jar
scp .\brain_dsecond_full.sql jetson:/tmp/brain_dsecond_full.sql
```

如果 Windows 路径有空格，建议用 WinSCP 或先压缩后上传。

---

## 2. Jetson 系统与硬件设置

本章全部在 **Jetson 端** 执行。

### 2.1 确认系统与 JetPack

执行位置：Jetson。

```bash
uname -a
cat /etc/os-release
cat /etc/nv_tegra_release
```

建议使用 JetPack 6.x 系列。具体 CUDA/TensorRT/PyTorch 版本以设备实际输出为准。

### 2.2 更新系统与安装基础软件

执行位置：Jetson。

```bash
sudo apt update
sudo apt upgrade -y
sudo apt install -y curl wget git unzip zip vim htop tmux build-essential pkg-config
sudo apt install -y nginx mysql-server openjdk-17-jdk maven
```

检查：

```bash
java -version
mvn -version
mysql --version
nginx -v
```

### 2.3 性能模式与散热

执行位置：Jetson。

```bash
sudo nvpmodel -q --verbose
```

找到最高性能模式，例如 `MAXN`、`MAXN_SUPER` 或类似名称，再执行：

```bash
sudo nvpmodel -m <最高性能模式编号>
sudo jetson_clocks
```

监控：

```bash
sudo tegrastats
```

注意：

- 不同 JetPack 下模式编号可能不同，先查再设置。
- 必须保证供电和散热稳定。

### 2.4 Swap

执行位置：Jetson。

```bash
sudo fallocate -l 8G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
free -h
```

### 2.5 目录规划

执行位置：Jetson。

```bash
sudo mkdir -p /opt/yds/apps/brain
sudo mkdir -p /opt/yds/apps/blade
sudo mkdir -p /opt/yds/apps/skin
sudo mkdir -p /opt/yds/services/yolo-v11-dmt
sudo mkdir -p /opt/yds/services/unet
sudo mkdir -p /opt/yds/services/mesh
sudo mkdir -p /opt/yds/services/blade-detect
sudo mkdir -p /opt/yds/services/skin-detect
sudo mkdir -p /opt/yds/models/brain/yolo
sudo mkdir -p /opt/yds/models/brain/unet
sudo mkdir -p /opt/yds/models/blade
sudo mkdir -p /opt/yds/models/skin
sudo mkdir -p /data/yds/uploads/brain
sudo mkdir -p /data/yds/uploads/blade
sudo mkdir -p /data/yds/uploads/skin
sudo mkdir -p /var/www/yds/brain
sudo mkdir -p /var/www/yds/blade
sudo mkdir -p /var/www/yds/skin
sudo mkdir -p /var/log/yds
sudo chown -R $USER:$USER /opt/yds /data/yds /var/www/yds /var/log/yds
```

---

## 3. Jetson 数据库部署

本章全部在 **Jetson 端** 执行。

### 3.1 启动 MySQL

执行位置：Jetson。

```bash
sudo systemctl enable mysql
sudo systemctl start mysql
sudo mysql_secure_installation
```

### 3.2 创建数据库用户

执行位置：Jetson，进入 MySQL。

```bash
sudo mysql
```

```sql
CREATE USER IF NOT EXISTS 'yds'@'localhost' IDENTIFIED BY '替换为强密码';
GRANT ALL PRIVILEGES ON *.* TO 'yds'@'localhost';
FLUSH PRIVILEGES;
```

### 3.3 创建三个数据库

执行位置：Jetson。

```sql
CREATE DATABASE IF NOT EXISTS dsecond DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS blade   DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS skin    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 3.4 导入 PC Navicat 导出的 SQL

执行位置：Jetson。

把 PC 导出的 SQL 放到：

```text
/opt/yds/db/
```

如果 SQL 文件内包含 `CREATE DATABASE` 和 `USE`：

```bash
mysql -u yds -p < /opt/yds/db/brain_dsecond_full.sql
mysql -u yds -p < /opt/yds/db/blade_full.sql
mysql -u yds -p < /opt/yds/db/skin_full.sql
```

如果 SQL 文件内不包含 `USE 数据库名`：

```bash
mysql -u yds -p dsecond < /opt/yds/db/brain_dsecond_full.sql
mysql -u yds -p blade   < /opt/yds/db/blade_full.sql
mysql -u yds -p skin    < /opt/yds/db/skin_full.sql
```

### 3.5 如果不用 Navicat 导出，则执行项目内初始化脚本

执行位置：Jetson。

brain：

```bash
mysql -u yds -p dsecond < /opt/yds/apps/brain/docs/database_init.sql
mysql -u yds -p dsecond < /opt/yds/apps/brain/docs/database_dataview_init.sql
mysql -u yds -p dsecond < /opt/yds/apps/brain/docs/database_config_init.sql
mysql -u yds -p dsecond < /opt/yds/apps/brain/docs/database_unet_init.sql
mysql -u yds -p dsecond < /opt/yds/apps/brain/create_admin_table.sql
```

blade：

```bash
mysql -u yds -p blade < /opt/yds/apps/blade/docs/database_init.sql
mysql -u yds -p blade < /opt/yds/apps/blade/docs/database_dataview_init.sql
mysql -u yds -p blade < /opt/yds/apps/blade/create_admin_table.sql
```

如果 blade 启用 JWT/RBAC、视频、系统配置：

```bash
mysql -u yds -p blade < /opt/yds/apps/blade/docs/jwt_rbac_init.sql
mysql -u yds -p blade < /opt/yds/apps/blade/docs/update_video_table.sql
mysql -u yds -p blade < /opt/yds/apps/blade/docs/system_config_init.sql
```

skin：

```bash
mysql -u yds -p < /opt/yds/apps/skin/docs/skin_database_init.sql
```

或分步：

```bash
mysql -u yds -p skin < /opt/yds/apps/skin/docs/database_init.sql
mysql -u yds -p skin < /opt/yds/apps/skin/docs/database_dataview_init.sql
mysql -u yds -p skin < /opt/yds/apps/skin/docs/update_video_table.sql
mysql -u yds -p skin < /opt/yds/apps/skin/docs/config_init.sql
mysql -u yds -p skin < /opt/yds/apps/skin/create_admin_table.sql
```

### 3.6 导入后必须检查

执行位置：Jetson 浏览器访问三个项目 Config 页面。

必须检查：

```text
后端 API 地址
Python 检测服务地址
YOLO 分割服务地址
UNet 分割服务地址
Mask 默认引擎
模型文件夹路径
模型文件完整路径
阈值配置
上传文件访问 URL
```

Windows 路径必须改成 Linux 路径：

```text
D:\algorithms\V11-dmt\best.pt
改为
/opt/yds/models/brain/yolo/best.engine
```

这里先说明最终路径形态，`.pt/.pth` 如何在 Jetson 本机导出 `.engine` 详见第 7 章。

---

## 4. Jetson 后端部署

本章主要在 **Jetson 端** 配置，jar 由 PC 构建后上传。

### 4.1 上传 jar 到指定路径

执行位置：Jetson。

```bash
cp /tmp/brain.jar /opt/yds/apps/brain/brain.jar
cp /tmp/blade.jar /opt/yds/apps/blade/blade.jar
cp /tmp/skin.jar /opt/yds/apps/skin/skin.jar
```

### 4.2 配置文件位置

执行位置：Jetson。

创建：

```bash
sudo mkdir -p /etc/yds/brain
sudo mkdir -p /etc/yds/blade
sudo mkdir -p /etc/yds/skin
sudo chown -R $USER:$USER /etc/yds
```

配置文件：

```text
/etc/yds/brain/application-prod.yml
/etc/yds/blade/application-prod.yml
/etc/yds/skin/application-prod.yml
```

### 4.3 brain 后端配置文件

配置文件：`/etc/yds/brain/application-prod.yml`

```yaml
server:
  port: 9527

spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: yds
    password: "替换为强密码"
    url: jdbc:mysql://localhost:3306/dsecond?useUnicode=true&characterEncoding=utf-8&allowMultiQueries=true&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 100MB

file:
  upload:
    path: /data/yds/uploads/brain/
  access:
    url: http://<jetson-ip>:8081/api/files/

python:
  detect:
    url: http://127.0.0.1:5001
  segment:
    url: http://127.0.0.1:3408
  mesh:
    url: http://127.0.0.1:5002

app:
  public-backend-url: http://<jetson-ip>:8081/api
  batch-output-root: /data/yds/uploads/brain/batch

jwt:
  secret: ${JWT_SECRET}
```

说明：

- 当前 brain 项目新增了 Config 页面里的 `YOLO 分割服务`、`UNet 分割服务`、`Mask 默认引擎`，这些在数据库 `system_config` 中保存。
- 外部 `application-prod.yml` 中的 `python.segment.url` 是兼容旧配置的默认值，Config 页面保存后会优先从数据库读取。

### 4.4 blade 后端配置文件

配置文件：`/etc/yds/blade/application-prod.yml`

```yaml
server:
  port: 1234

spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: yds
    password: "替换为强密码"
    url: jdbc:mysql://localhost:3306/blade?useUnicode=true&characterEncoding=utf-8&allowMultiQueries=true&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true

file:
  upload:
    path: /data/yds/uploads/blade/
  access:
    url: http://<jetson-ip>:8082/api/files/

python:
  detect:
    url: http://127.0.0.1:6522
  video:
    url: http://127.0.0.1:6522
```

对应源码默认配置文件：

```text
D:\YDS\blade - 未改UI\springb\src\main\resources\application.yml
```

### 4.5 skin 后端配置文件

配置文件：`/etc/yds/skin/application-prod.yml`

```yaml
server:
  port: 1907

spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: yds
    password: "替换为强密码"
    url: jdbc:mysql://localhost:3306/skin?useUnicode=true&characterEncoding=utf-8&allowMultiQueries=true&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true

file:
  upload:
    path: /data/yds/uploads/skin/
  access:
    url: http://<jetson-ip>:8083/api/files/

python:
  detect:
    url: http://127.0.0.1:2026
  video:
    url: http://127.0.0.1:2026
```

对应源码默认配置文件：

```text
D:\YDS\skin\springb\src\main\resources\application.yml
```

### 4.6 systemd 服务文件

执行位置：Jetson。  
配置文件目录：`/etc/systemd/system/`。

brain：`/etc/systemd/system/yds-brain.service`

```ini
[Unit]
Description=YDS Brain Spring Boot
After=network.target mysql.service

[Service]
User=yds
WorkingDirectory=/opt/yds/apps/brain
Environment=JWT_SECRET=替换为强随机密钥
ExecStart=/usr/bin/java -Xms256m -Xmx768m -jar /opt/yds/apps/brain/brain.jar --spring.config.additional-location=/etc/yds/brain/application-prod.yml
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

blade：`/etc/systemd/system/yds-blade.service`

```ini
[Unit]
Description=YDS Blade Spring Boot
After=network.target mysql.service

[Service]
User=yds
WorkingDirectory=/opt/yds/apps/blade
ExecStart=/usr/bin/java -Xms256m -Xmx768m -jar /opt/yds/apps/blade/blade.jar --spring.config.additional-location=/etc/yds/blade/application-prod.yml
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

skin：`/etc/systemd/system/yds-skin.service`

```ini
[Unit]
Description=YDS Skin Spring Boot
After=network.target mysql.service

[Service]
User=yds
WorkingDirectory=/opt/yds/apps/skin
ExecStart=/usr/bin/java -Xms256m -Xmx768m -jar /opt/yds/apps/skin/skin.jar --spring.config.additional-location=/etc/yds/skin/application-prod.yml
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

启动：

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now yds-brain
sudo systemctl enable --now yds-blade
sudo systemctl enable --now yds-skin
```

检查：

```bash
systemctl status yds-brain
systemctl status yds-blade
systemctl status yds-skin
```

---

## 5. Jetson 前端与 Nginx 反代

本章在 **Jetson 端** 配置，`dist` 由 PC 构建后上传。

### 5.1 放置前端 dist

执行位置：Jetson。

```bash
cp -r /tmp/brain-dist/* /var/www/yds/brain/
cp -r /tmp/blade-dist/* /var/www/yds/blade/
cp -r /tmp/skin-dist/* /var/www/yds/skin/
```

### 5.2 Nginx 配置文件

配置文件：`/etc/nginx/sites-available/yds`

```nginx
server {
    listen 8081;
    server_name _;
    root /var/www/yds/brain;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:9527/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}

server {
    listen 8082;
    server_name _;
    root /var/www/yds/blade;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:1234/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}

server {
    listen 8083;
    server_name _;
    root /var/www/yds/skin;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:1907/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

启用：

```bash
sudo ln -s /etc/nginx/sites-available/yds /etc/nginx/sites-enabled/yds
sudo nginx -t
sudo systemctl reload nginx
```

### 5.3 前端 Config 页面需要配置

执行位置：浏览器访问 Jetson 前端页面。

brain：

```text
访问：http://<jetson-ip>:8081
Config 页面配置：
  后端 API 地址：http://<jetson-ip>:8081/api
  Python 检测服务：http://127.0.0.1:5001
  YOLO 分割服务：http://127.0.0.1:5001
  UNet 分割服务：http://127.0.0.1:3408
  Mask 默认引擎：UNet 或 YOLO
```

blade：

```text
访问：http://<jetson-ip>:8082
如果有 Config 页面：
  后端 API 地址：http://<jetson-ip>:8082/api
  Python 检测服务：http://127.0.0.1:6522
```

skin：

```text
访问：http://<jetson-ip>:8083
如果有 Config 页面：
  后端 API 地址：http://<jetson-ip>:8083/api
  Python 检测服务：http://127.0.0.1:2026
```

---

## 6. Jetson Python 模型服务部署

本章专门说明检测、分割、Mesh 服务。结论先放前面：

- **不建议长期把整个算法训练工程搬到 Jetson**。更好的方式是做“运行包”：入口脚本、必要本地模块、模型文件、依赖清单、启动脚本。
- **当前 YOLO 三个入口脚本不能只拷贝单个 `.py` 文件**，因为 `D:\algorithms\V11-dmt` 内有本地 `ultralytics` 源码目录。如果 PC 端模型依赖这套改过的 `ultralytics`，Jetson 端也必须带上该目录或把它安装成包。
- **降级方案可以整包拷贝算法目录**，但 systemd 必须设置 `WorkingDirectory`，否则会出现“换目录运行就缺模块”的问题。

### 6.1 五个 Python 服务现状

| 服务 | PC 入口脚本 | Jetson 建议入口 | 端口 | 当前主要依赖 | 是否可瘦身 |
|---|---|---|---:|---|---|
| brain YOLO 检测/分割 | `D:\algorithms\V11-dmt\detect-api.py` | `/opt/yds/services/brain-yolo/detect-api.py` | `5001` | Flask、ultralytics、Pillow、numpy、torch/TensorRT | 可以，但要处理本地 `ultralytics` |
| brain UNet 分割 | `D:\algorithms\Pytorch-UNet-master\unet4threedim.py` | `/opt/yds/services/brain-unet/unet4threedim.py` | `3408` | Flask、Pillow、numpy、scipy、opencv、torch | 可以，入口脚本基本自包含 |
| blade 检测/视频 | `D:\algorithms\V11-dmt\detect-api-blade.py` | `/opt/yds/services/blade-detect/detect-api-blade.py` | `6522` | Flask、ultralytics、opencv、ffmpeg、torch/TensorRT | 可以，但要改模型根目录 |
| skin 检测/视频 | `D:\algorithms\V11-dmt\detect-api-skin.py` | `/opt/yds/services/skin-detect/detect-api-skin.py` | `2026` | Flask、ultralytics、opencv、ffmpeg、torch/TensorRT | 可以，但要改模型根目录 |
| Mesh 三维重建 | `detect_service/mesh-api.py` | `/opt/yds/services/mesh/mesh-api.py` | `5002` | Flask、numpy、scipy、monai 可选 | 可以，入口脚本基本自包含 |

### 6.2 推荐的 Jetson 目录结构

执行位置：Jetson。

```text
/opt/yds/services/
  brain-yolo/
    detect-api.py
    ultralytics/              # 如果使用 V11-dmt 本地改造版 ultralytics，则必须带上
    requirements-jetson.txt
    uploads/
    results/
  brain-unet/
    unet4threedim.py
    requirements-jetson.txt
  blade-detect/
    detect-api-blade.py
    ultralytics/
    requirements-jetson.txt
    uploads/
    results/
    static/generated_videos/
  skin-detect/
    detect-api-skin.py
    ultralytics/
    requirements-jetson.txt
    uploads/
    results/
    static/generated_videos/
  mesh/
    mesh-api.py
    requirements-jetson.txt

/opt/yds/models/
  brain/yolo/
  brain/unet/
  blade/
  skin/
```

如果不确定本地 `ultralytics` 有没有改动，先按“带上本地 `ultralytics/`”处理。这样最接近 PC 端行为。

### 6.3 PC 端制作瘦身运行包

执行位置：PC。

brain YOLO 最小运行包：

```powershell
mkdir D:\deploy\brain-yolo
copy D:\algorithms\V11-dmt\detect-api.py D:\deploy\brain-yolo\
xcopy D:\algorithms\V11-dmt\ultralytics D:\deploy\brain-yolo\ultralytics /E /I
```

blade 运行包：

```powershell
mkdir D:\deploy\blade-detect
copy D:\algorithms\V11-dmt\detect-api-blade.py D:\deploy\blade-detect\
xcopy D:\algorithms\V11-dmt\ultralytics D:\deploy\blade-detect\ultralytics /E /I
```

skin 运行包：

```powershell
mkdir D:\deploy\skin-detect
copy D:\algorithms\V11-dmt\detect-api-skin.py D:\deploy\skin-detect\
xcopy D:\algorithms\V11-dmt\ultralytics D:\deploy\skin-detect\ultralytics /E /I
```

UNet 运行包：

```powershell
mkdir D:\deploy\brain-unet
copy D:\algorithms\Pytorch-UNet-master\unet4threedim.py D:\deploy\brain-unet\
```

Mesh 运行包：

```powershell
mkdir D:\deploy\mesh
copy D:\YDS\code\detect_service\mesh-api.py D:\deploy\mesh\
```

把 `D:\deploy\...` 上传到 Jetson 对应目录。

### 6.4 Jetson 端降级整包方案

执行位置：Jetson。

如果瘦身运行包启动失败，使用降级方案：把算法目录整体上传。

```text
D:\algorithms\V11-dmt
上传到
/opt/yds/services/V11-dmt

D:\algorithms\Pytorch-UNet-master
上传到
/opt/yds/services/Pytorch-UNet-master
```

然后 systemd 必须这样配置：

```ini
WorkingDirectory=/opt/yds/services/V11-dmt
ExecStart=/home/yds/miniforge3/envs/v11dmt/bin/python /opt/yds/services/V11-dmt/detect-api.py
```

这种方式占空间，但最容易复现 PC 端运行环境。后续稳定后再逐步瘦身。

### 6.5 安装 Miniforge

执行位置：Jetson。

```bash
cd /tmp
wget https://github.com/conda-forge/miniforge/releases/latest/download/Miniforge3-Linux-aarch64.sh
bash Miniforge3-Linux-aarch64.sh
source ~/miniforge3/bin/activate
conda config --set auto_activate_base false
```

### 6.6 创建 Jetson 环境

执行位置：Jetson。

建议先分成三个环境，减少冲突：

```bash
conda create -n v11dmt python=3.10 -y
conda create -n unet python=3.10 -y
conda create -n mesh python=3.10 -y
```

说明：

- PC 的 `v11dmt`、`unet` 环境不能直接复制到 Jetson，因为 PC 多半是 `win-64`，Jetson 是 `linux-aarch64`。
- 不要在 Jetson 上直接用 PC 的 `environment.yml` 全量安装，容易把 torch、opencv、numpy 拉到不兼容版本。
- `.engine` 推理需要 TensorRT。TensorRT Python 包通常来自 JetPack 系统环境，conda 环境可能默认看不到；导出和验证步骤详见第 7 章。

### 6.7 检查 Jetson AI 栈

执行位置：Jetson，先用系统 Python 检查。

```bash
python3 -c "import tensorrt as trt; print('tensorrt', trt.__version__)"
python3 -c "import torch; print('torch', torch.__version__, torch.cuda.is_available())"
python3 -c "import cv2; print('cv2', cv2.__version__)"
```

如果系统 Python 能 import TensorRT，但 conda 里不能 import，有两种做法：

- 推荐做法：检测服务改用系统 Python venv，并启用 `--system-site-packages`。
- 兼容做法：在 systemd 里给 conda 服务加 `PYTHONPATH=/usr/lib/python3/dist-packages`，让 conda 能看到系统 TensorRT。

### 6.8 v11dmt 环境安装

执行位置：Jetson。

这个环境用于：

```text
/opt/yds/services/brain-yolo/detect-api.py
/opt/yds/services/blade-detect/detect-api-blade.py
/opt/yds/services/skin-detect/detect-api-skin.py
```

推荐安装顺序：

```bash
conda activate v11dmt
python -m pip install --upgrade pip
python -m pip install flask==3.0.3 flask-cors==4.0.1 requests pillow==10.3.0 numpy==1.26.4
python -m pip install pyyaml tqdm pandas matplotlib seaborn psutil py-cpuinfo scipy
python -m pip install imageio-ffmpeg
```

安装 ultralytics 有两个选择。

选择 A：使用 PC 项目内本地 `ultralytics/`，更接近当前 V11-dmt 行为：

```bash
cd /opt/yds/services/brain-yolo
python -c "from ultralytics import YOLO; print('local ultralytics ok')"
```

选择 B：使用 pip 官方 ultralytics，只有在确认模型和代码兼容时使用：

```bash
python -m pip install ultralytics==8.2.0 --no-deps
```

opencv 建议先用 conda-forge：

```bash
conda install -c conda-forge opencv -y
```

如果 conda-forge opencv 安装冲突，再考虑系统 Python venv 方案，不建议在 Jetson 上长时间源码编译 opencv。

安装 ffmpeg：

```bash
sudo apt install -y ffmpeg
```

验证：

```bash
python -c "from ultralytics import YOLO; print('YOLO import ok')"
python -c "import cv2; print('cv2', cv2.__version__)"
python -c "import torch; print('torch', torch.__version__, torch.cuda.is_available())"
```

### 6.9 unet 环境安装

执行位置：Jetson。

```bash
conda activate unet
python -m pip install --upgrade pip
python -m pip install flask==3.0.3 flask-cors==4.0.1 requests pillow==10.3.0 numpy==1.26.4 scipy
conda install -c conda-forge opencv -y
```

如果 `unet4threedim.py` 调用 `.pth`，必须安装 Jetson 对应 PyTorch。按 JetPack 对应版本安装 NVIDIA 提供的 PyTorch wheel，不使用 PC 端 torch。

验证：

```bash
python -c "import flask, PIL, numpy, scipy, cv2; print('unet deps ok')"
python -c "import torch; print(torch.__version__, torch.cuda.is_available())"
```

如果暂时没有 PyTorch，`unet4threedim.py` 仍可能启动，但真实 `.pth` 推理会失败，只能走 fallback；生产环境必须让 torch 可用。

### 6.10 mesh 环境安装

执行位置：Jetson。

```bash
conda activate mesh
python -m pip install --upgrade pip
python -m pip install flask==3.0.3 flask-cors==4.0.1 numpy==1.26.4 scipy
python -m pip install monai
```

说明：

- `mesh-api.py` 当前主要依赖 numpy/scipy。
- `monai` 是增强归一化用的，可用则使用，不可用时服务会降级到 percentile 归一化。
- Mesh 服务不负责模型推理，主要根据分割体数据构建前端可用的 mesh JSON。

### 6.11 Jetson 必改脚本配置

执行位置：Jetson 或 PC 打包前修改。

`detect-api-blade.py` 里当前有 Windows 路径：

```python
DEFAULT_MODEL_ROOT = r"D:\algorithms\V11-dmt\runs\race-blade"
```

Jetson 应改为：

```python
DEFAULT_MODEL_ROOT = "/opt/yds/models/blade"
```

`detect-api-skin.py` 里当前有 Windows 路径：

```python
DEFAULT_MODEL_ROOT = r"D:\algorithms\V11-dmt\runs\skin-cancer"
```

Jetson 应改为：

```python
DEFAULT_MODEL_ROOT = "/opt/yds/models/skin"
```

`detect-api.py` 使用相对目录：

```python
UPLOAD_FOLDER = "uploads"
RESULT_FOLDER = "results"
```

因此 systemd 必须配置：

```ini
WorkingDirectory=/opt/yds/services/brain-yolo
```

UNet 的端口由命令行或环境变量控制：

```bash
UNET_PORT=3408
```

Mesh 的端口由环境变量控制：

```bash
MESH_API_PORT=5002
```

### 6.12 Python 服务 systemd 文件

配置文件目录：`/etc/systemd/system/`。

brain YOLO：`/etc/systemd/system/yds-brain-yolo.service`

```ini
[Unit]
Description=YDS Brain YOLO Service
After=network-online.target
PartOf=yds.target

[Service]
User=yds
WorkingDirectory=/opt/yds/services/brain-yolo
ExecStart=/home/yds/miniforge3/envs/v11dmt/bin/python /opt/yds/services/brain-yolo/detect-api.py
Environment=PYTHONUNBUFFERED=1
Environment=PYTHONPATH=/usr/lib/python3/dist-packages
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

brain UNet：`/etc/systemd/system/yds-brain-unet.service`

```ini
[Unit]
Description=YDS Brain UNet Segment Service
After=network-online.target
PartOf=yds.target

[Service]
User=yds
WorkingDirectory=/opt/yds/services/brain-unet
ExecStart=/home/yds/miniforge3/envs/unet/bin/python /opt/yds/services/brain-unet/unet4threedim.py --host 127.0.0.1 --port 3408
Environment=PYTHONUNBUFFERED=1
Environment=UNET_DEVICE=cuda
Environment=PYTHONPATH=/usr/lib/python3/dist-packages
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

Mesh：`/etc/systemd/system/yds-mesh.service`

```ini
[Unit]
Description=YDS Mesh Service
After=network-online.target
PartOf=yds.target

[Service]
User=yds
WorkingDirectory=/opt/yds/services/mesh
Environment=MESH_API_PORT=5002
Environment=PYTHONUNBUFFERED=1
ExecStart=/home/yds/miniforge3/envs/mesh/bin/python /opt/yds/services/mesh/mesh-api.py
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

blade：`/etc/systemd/system/yds-blade-detect.service`

```ini
[Unit]
Description=YDS Blade Detect Service
After=network-online.target
PartOf=yds.target

[Service]
User=yds
WorkingDirectory=/opt/yds/services/blade-detect
ExecStart=/home/yds/miniforge3/envs/v11dmt/bin/python /opt/yds/services/blade-detect/detect-api-blade.py
Environment=PYTHONUNBUFFERED=1
Environment=FFMPEG_BINARY=/usr/bin/ffmpeg
Environment=PYTHONPATH=/usr/lib/python3/dist-packages
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

skin：`/etc/systemd/system/yds-skin-detect.service`

```ini
[Unit]
Description=YDS Skin Detect Service
After=network-online.target
PartOf=yds.target

[Service]
User=yds
WorkingDirectory=/opt/yds/services/skin-detect
ExecStart=/home/yds/miniforge3/envs/v11dmt/bin/python /opt/yds/services/skin-detect/detect-api-skin.py
Environment=PYTHONUNBUFFERED=1
Environment=FFMPEG_BINARY=/usr/bin/ffmpeg
Environment=PYTHONPATH=/usr/lib/python3/dist-packages
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

启用单个服务：

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now yds-brain-yolo
sudo systemctl enable --now yds-brain-unet
sudo systemctl enable --now yds-mesh
sudo systemctl enable --now yds-blade-detect
sudo systemctl enable --now yds-skin-detect
```

### 6.13 服务健康检查

执行位置：Jetson。

```bash
curl http://127.0.0.1:5001/health
curl http://127.0.0.1:3408/health
curl http://127.0.0.1:5002/mesh/health
curl http://127.0.0.1:6522/health
curl http://127.0.0.1:2026/health
```

日志查看：

```bash
journalctl -u yds-brain-yolo -f
journalctl -u yds-brain-unet -f
journalctl -u yds-mesh -f
journalctl -u yds-blade-detect -f
journalctl -u yds-skin-detect -f
```

---

## 7. 模型导出 TensorRT engine 与验证

本章主要在 **Jetson 端** 执行。

### 7.1 模型路径

Jetson 推荐路径：

```text
/opt/yds/models/brain/yolo/best.engine
/opt/yds/models/brain/unet/best.engine
/opt/yds/models/blade/best.engine
/opt/yds/models/skin/best.engine
```

PC 端可以先上传 `.pt` 或 `.pth`：

```text
/opt/yds/models/brain/yolo/best.pt
/opt/yds/models/brain/unet/best.pth
/opt/yds/models/blade/best.pt
/opt/yds/models/skin/best.pt
```

注意：

- `.engine` 最好在 Jetson 本机导出。
- PC 导出的 `.engine` 不保证能在 Jetson 上用。
- `.engine` 和 Jetson 的 GPU、TensorRT、CUDA、JetPack 版本强相关。
- YOLO 模型可以直接用 Ultralytics 导出 `.engine`。
- UNet 当前服务代码如果没有 TensorRT 推理后端，即使导出了 `.engine`，也不能直接被 `unet4threedim.py` 正确调用。

### 7.2 Jetson 导出前检查

执行位置：Jetson。

```bash
cat /etc/nv_tegra_release
python3 -c "import tensorrt as trt; print('TensorRT', trt.__version__)"
trtexec --version
```

在 YOLO 环境中检查：

```bash
conda activate v11dmt
python -c "import torch; print('torch', torch.__version__, torch.cuda.is_available())"
python -c "from ultralytics import YOLO; print('ultralytics ok')"
```

如果 conda 环境找不到 TensorRT，但系统 Python 可以找到，可以临时执行：

```bash
export PYTHONPATH=/usr/lib/python3/dist-packages:$PYTHONPATH
```

systemd 中也应保留：

```ini
Environment=PYTHONPATH=/usr/lib/python3/dist-packages
```

### 7.3 YOLO 模型在 Jetson 导出 engine

执行位置：Jetson。

brain YOLO：

```bash
conda activate v11dmt
cd /opt/yds/models/brain/yolo

python - <<'PY'
from ultralytics import YOLO

model = YOLO("best.pt")
out = model.export(
    format="engine",
    imgsz=640,
    batch=1,
    device=0,
    half=True,
    dynamic=False,
    simplify=True,
    workspace=2,
)
print(out)
PY
```

blade：

```bash
conda activate v11dmt
cd /opt/yds/models/blade

python - <<'PY'
from ultralytics import YOLO

model = YOLO("best.pt")
out = model.export(format="engine", imgsz=640, batch=1, device=0, half=True, dynamic=False, simplify=True, workspace=2)
print(out)
PY
```

skin：

```bash
conda activate v11dmt
cd /opt/yds/models/skin

python - <<'PY'
from ultralytics import YOLO

model = YOLO("best.pt")
out = model.export(format="engine", imgsz=640, batch=1, device=0, half=True, dynamic=False, simplify=True, workspace=2)
print(out)
PY
```

也可以使用 CLI：

```bash
yolo export model=/opt/yds/models/brain/yolo/best.pt format=engine imgsz=640 batch=1 device=0 half=True dynamic=False simplify=True workspace=2
```

参数建议：

| 参数 | 建议 | 说明 |
|---|---|---|
| `imgsz` | 与训练/推理一致，例如 `640` | 输入尺寸必须和项目配置一致 |
| `batch` | `1` | Jetson 端优先保证稳定 |
| `device` | `0` | 使用 Jetson GPU |
| `half` | `True` | FP16，一般适合 Jetson |
| `dynamic` | `False` | 固定输入尺寸，engine 更稳定 |
| `workspace` | `2` 或更高 | 单位通常为 GB，空间不足时调小 |
| `int8` | 先不用 | INT8 需要校准数据，先跑通 FP16 |

导出完成后，通常会在 `.pt` 同目录生成：

```text
best.engine
```

### 7.4 YOLO engine 推理验证

执行位置：Jetson。

检测模型验证：

```bash
conda activate v11dmt
python - <<'PY'
from ultralytics import YOLO

model = YOLO("/opt/yds/models/blade/best.engine")
result = model.predict("/opt/yds/test/test.jpg", imgsz=640, conf=0.25, device=0, verbose=False)[0]
print("boxes:", 0 if result.boxes is None else len(result.boxes))
print("ENGINE_OK")
PY
```

分割模型验证：

```bash
conda activate v11dmt
python - <<'PY'
from ultralytics import YOLO

model = YOLO("/opt/yds/models/brain/yolo/best.engine")
result = model.predict("/opt/yds/test/test.jpg", imgsz=640, conf=0.25, device=0, verbose=False)[0]
print("boxes:", 0 if result.boxes is None else len(result.boxes))
print("masks:", "none" if result.masks is None else len(result.masks))
print("ENGINE_OK")
PY
```

如果是 YOLO 分割模型，`result.masks` 应该不是 `None`。如果是普通检测模型，只有 `boxes` 没有 `masks` 是正常的。

### 7.5 UNet 模型导出 engine 的处理方式

执行位置：Jetson。

当前 `D:\algorithms\Pytorch-UNet-master\unet4threedim.py` 的主要推理链路是：

```text
.pth/.pt -> PyTorch -> mask
```

虽然接口参数里有 `model_format=engine` 的概念，但当前代码如果没有 TensorRT Runtime 推理实现，导出的 `.engine` 不能直接替代 `.pth` 使用。

UNet 有两种路线：

#### 路线 A：先保持 UNet 使用 `.pth`

这是当前最稳的上线方式。

配置：

```text
UNet 模型路径：/opt/yds/models/brain/unet/best.pth
UNet 服务：/opt/yds/services/brain-unet/unet4threedim.py
```

优点：

- 和当前服务代码匹配。
- 风险最低。

缺点：

- 推理速度可能不如 TensorRT。

#### 路线 B：先导出 ONNX，再用 TensorRT 生成 engine

这条路线需要后续给 `unet4threedim.py` 增加 TensorRT 推理后端，否则只能完成导出和验证，不能直接被现有服务调用。

步骤 1：在 Jetson 上先导出 ONNX。

需要一个 UNet ONNX 导出脚本，核心逻辑如下：

```python
import torch
from unet4threedim import load_pytorch_model

model_path = "/opt/yds/models/brain/unet/best.pth"
onnx_path = "/opt/yds/models/brain/unet/best.onnx"
input_size = 256
n_channels = 3
device = "cuda"

model = load_pytorch_model(model_path, n_channels, device)
dummy = torch.randn(1, n_channels, input_size, input_size, device=device)

torch.onnx.export(
    model,
    dummy,
    onnx_path,
    input_names=["input"],
    output_names=["output"],
    opset_version=17,
    dynamic_axes=None,
)

print(onnx_path)
```

步骤 2：用 TensorRT 生成 engine。

```bash
trtexec \
  --onnx=/opt/yds/models/brain/unet/best.onnx \
  --saveEngine=/opt/yds/models/brain/unet/best.engine \
  --fp16 \
  --workspace=2048
```

步骤 3：验证 engine 能被 TensorRT 加载。

```bash
trtexec \
  --loadEngine=/opt/yds/models/brain/unet/best.engine \
  --shapes=input:1x3x256x256
```

如果 ONNX 导出时使用的是固定输入尺寸，`--shapes` 可以不写；如果使用动态输入尺寸，必须写实际输入名和尺寸。

### 7.6 项目配置改成 engine 路径

执行位置：浏览器访问 Jetson 前端 Config 页面。

brain YOLO：

```text
模型路径：/opt/yds/models/brain/yolo/best.engine
模型格式：engine 或 tensorrt
```

blade：

```text
模型路径：/opt/yds/models/blade/best.engine
模型格式：engine 或 tensorrt
```

skin：

```text
模型路径：/opt/yds/models/skin/best.engine
模型格式：engine 或 tensorrt
```

brain UNet：

```text
如果仍使用当前 PyTorch 推理链路：
  模型路径：/opt/yds/models/brain/unet/best.pth
  模型格式：pytorch

如果后续已实现 TensorRT 推理后端：
  模型路径：/opt/yds/models/brain/unet/best.engine
  模型格式：engine 或 tensorrt
```

### 7.7 engine 验证

执行位置：Jetson。

参考：

```text
docs/engine兼容性最小验证流程.md
```

最小验证：

```bash
python3 - <<'PY'
from ultralytics import YOLO
model = YOLO("/opt/yds/models/brain/yolo/best.engine")
result = model.predict("test.jpg", imgsz=640, conf=0.25)[0]
print("boxes:", 0 if result.boxes is None else len(result.boxes))
if result.masks is not None:
    print("masks:", len(result.masks))
print("ENGINE_COMPATIBLE")
PY
```

通过后再把 `.engine` 路径配置到项目 Config 页面。

---

## 8. 算法服务工程化包装与一键启动

本章说明如何从“能跑”升级到“好部署”。

### 8.1 三种部署等级

| 等级 | 方案 | PC 端操作 | Jetson 端操作 | 适合阶段 |
|---|---|---|---|---|
| A | 标准 Python 包 | 重构为 `pyproject.toml` + package | `pip install -e .` 或安装 wheel | 长期维护 |
| B | 瘦身运行包 | 只打包入口脚本、必要本地模块、依赖清单 | 放到 `/opt/yds/services/...` 后 systemd 启动 | 推荐当前阶段 |
| C | 整个算法文件夹 | 原样上传 `V11-dmt`、`Pytorch-UNet-master` | 设置 `WorkingDirectory` 后运行 | 降级兜底 |

当前建议先用 **B 瘦身运行包**，失败时立刻用 **C 整包兜底**，稳定后再做 **A 标准包**。

### 8.2 标准 Python 包方向

PC 端目标结构示例：

```text
yds-algorithm-services/
  pyproject.toml
  src/
    yds_algorithms/
      __init__.py
      brain_yolo_api.py
      brain_unet_api.py
      blade_api.py
      skin_api.py
      mesh_api.py
    ultralytics/              # 如果确实依赖本地改造版，则作为包或 vendor 保留
```

`pyproject.toml` 示例：

```toml
[project]
name = "yds-algorithm-services"
version = "0.1.0"
requires-python = ">=3.10"
dependencies = [
  "flask==3.0.3",
  "flask-cors==4.0.1",
  "pillow==10.3.0",
  "numpy==1.26.4",
  "scipy",
  "requests",
  "pyyaml",
  "tqdm",
  "pandas",
  "matplotlib",
  "seaborn",
  "psutil",
  "py-cpuinfo"
]

[project.scripts]
yds-brain-yolo = "yds_algorithms.brain_yolo_api:main"
yds-brain-unet = "yds_algorithms.brain_unet_api:main"
yds-blade-detect = "yds_algorithms.blade_api:main"
yds-skin-detect = "yds_algorithms.skin_api:main"
yds-mesh = "yds_algorithms.mesh_api:main"

[build-system]
requires = ["setuptools>=68", "wheel"]
build-backend = "setuptools.build_meta"
```

Jetson 端安装：

```bash
conda activate v11dmt
cd /opt/yds/services/yds-algorithm-services
pip install -e .
```

长期好处：

- 不再依赖“必须在某个目录下运行”。
- systemd 可以直接执行命令，例如 `yds-brain-yolo --port 5001`。
- PC 和 Jetson 共用同一套代码结构，只区分依赖安装方式和模型路径。
- 后期可以构建 wheel，Jetson 端只安装 wheel，不需要携带训练数据、runs、docs、tests。

### 8.3 潜在冲突清单

| 冲突点 | 表现 | 建议 |
|---|---|---|
| PC conda 环境直接复制 | Jetson 无法使用 win-64 包 | Jetson 重新安装 linux-aarch64 依赖 |
| torch 版本不匹配 | `torch.cuda.is_available()` 为 false 或 import 失败 | 按 JetPack 版本安装 NVIDIA PyTorch |
| TensorRT 在 conda 不可见 | `.engine` 加载失败，提示找不到 tensorrt | systemd 加 `PYTHONPATH=/usr/lib/python3/dist-packages` 或改用 system venv；导出与验证详见第 7 章 |
| ultralytics 自动安装依赖 | 覆盖 torch/numpy/opencv | `pip install ultralytics==8.2.0 --no-deps`，依赖手动装 |
| opencv 安装冲突 | `import cv2` 失败或拉取大量包 | 优先 conda-forge opencv；失败再换 system venv |
| numpy 版本过高 | scipy/opencv/torch ABI 报错 | 当前文档固定 `numpy==1.26.4` |
| blade/skin 默认模型根目录 | 仍指向 `D:\algorithms\...` | 改成 `/opt/yds/models/blade`、`/opt/yds/models/skin` |
| 视频编码 | 结果视频浏览器打不开 | 安装 `ffmpeg`，服务配置 `FFMPEG_BINARY=/usr/bin/ffmpeg` |

### 8.4 一两个指令启动整个项目

执行位置：Jetson。

创建 systemd target：`/etc/systemd/system/yds.target`

```ini
[Unit]
Description=YDS All Projects
Wants=mysql.service nginx.service yds-brain.service yds-blade.service yds-skin.service yds-brain-yolo.service yds-brain-unet.service yds-mesh.service yds-blade-detect.service yds-skin-detect.service
After=network-online.target mysql.service nginx.service

[Install]
WantedBy=multi-user.target
```

所有 `yds-*.service` 的 `[Unit]` 里建议加：

```ini
PartOf=yds.target
```

启动全部：

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now yds.target
```

日常启动、停止、查看状态：

```bash
sudo systemctl start yds.target
sudo systemctl stop yds.target
systemctl list-units "yds-*"
```

也可以额外放一个脚本：`/opt/yds/bin/start-yds.sh`

```bash
#!/usr/bin/env bash
set -e
sudo systemctl start mysql nginx
sudo systemctl start yds-brain yds-blade yds-skin
sudo systemctl start yds-brain-yolo yds-brain-unet yds-mesh yds-blade-detect yds-skin-detect
systemctl --no-pager --type=service --state=running | grep yds || true
```

授权后启动：

```bash
sudo chmod +x /opt/yds/bin/start-yds.sh
/opt/yds/bin/start-yds.sh
```

### 8.5 项目调用关系确认

启动成功后，需要确认三个项目配置指向 Jetson 本机服务。

brain Config 页面：

```text
Python 检测服务地址：http://127.0.0.1:5001
YOLO 分割服务地址：http://127.0.0.1:5001
UNet 分割服务地址：http://127.0.0.1:3408
Mask 默认引擎：unet 或 yolo
Mesh 服务地址：http://127.0.0.1:5002
```

blade Config 页面：

```text
Python 检测服务地址：http://127.0.0.1:6522
模型根目录：/opt/yds/models/blade
```

skin Config 页面：

```text
Python 检测服务地址：http://127.0.0.1:2026
模型根目录：/opt/yds/models/skin
```

如果前端通过后端调用 Python 服务，Python 服务地址可以配置为 `127.0.0.1`。如果前端浏览器要直接访问 Python 返回的视频或图片 URL，则不能返回 `localhost`，需要改成 `http://<jetson-ip>:端口/...` 或由 Nginx 反代。

---

## 9. 最小上线顺序

### 9.1 PC 端

1. 构建三个前端 `dist`。
2. 构建三个后端 jar。
3. 用 Navicat 导出三个数据库 SQL。
4. 整理模型文件和算法服务代码。
5. 上传 `dist`、jar、SQL、模型、Python 服务代码到 Jetson。

### 9.2 Jetson 端

1. 系统更新，安装基础软件。
2. 设置性能模式、swap、目录。
3. 安装并初始化 MySQL。
4. 导入 PC 导出的 SQL。
5. 安装 Miniforge，创建 Python 环境。
6. 安装 YOLO/UNet/Mesh 依赖。
7. 按第 7 章在 Jetson 本机导出并验证 `.engine`。
8. 配置并启动 Python 服务。
9. 配置并启动 Spring Boot 服务。
10. 放置前端 `dist`，配置 Nginx。
11. 访问三个前端 Config 页面，修正 Jetson 路径和服务地址。
12. 逐个测试上传、检测、分割、视频、3D 功能。
13. 压测并观察 `tegrastats`。

---

## 10. 文件配置总表

### 10.1 PC 端源码配置文件

| 项目 | 文件 | 用途 |
|---|---|---|
| brain | `D:\YDS\code\vue\package.json` | 前端依赖与构建 |
| brain | `D:\YDS\code\springb\pom.xml` | 后端依赖与打包 |
| brain | `D:\YDS\code\springb\src\main\resources\application.yml` | 本地开发默认配置 |
| brain | `D:\YDS\code\detect_service\config-jetson.yml` | Jetson Python 服务参考配置 |
| blade | `D:\YDS\blade - 未改UI\springb\src\main\resources\application.yml` | 本地开发默认配置 |
| skin | `D:\YDS\skin\springb\src\main\resources\application.yml` | 本地开发默认配置 |

### 10.2 Jetson 端生产配置文件

| 文件 | 配置内容 |
|---|---|
| `/etc/yds/brain/application-prod.yml` | brain 端口、数据库、uploads、Python 服务 URL |
| `/etc/yds/blade/application-prod.yml` | blade 端口、数据库、uploads、Python 服务 URL |
| `/etc/yds/skin/application-prod.yml` | skin 端口、数据库、uploads、Python 服务 URL |
| `/etc/nginx/sites-available/yds` | 三个前端和 `/api` 反代 |
| `/etc/systemd/system/yds-brain.service` | brain 后端服务 |
| `/etc/systemd/system/yds-blade.service` | blade 后端服务 |
| `/etc/systemd/system/yds-skin.service` | skin 后端服务 |
| `/etc/systemd/system/yds-brain-yolo.service` | brain YOLO 服务 |
| `/etc/systemd/system/yds-brain-unet.service` | brain UNet 服务 |
| `/etc/systemd/system/yds-mesh.service` | Mesh 服务 |

### 10.3 Web Config 页面配置项

执行位置：浏览器访问 Jetson 前端。

brain Config 页面：

```text
后端 API 地址：http://<jetson-ip>:8081/api
Python 检测服务：http://127.0.0.1:5001
YOLO 分割服务：http://127.0.0.1:5001
UNet 分割服务：http://127.0.0.1:3408
Mask 默认引擎：UNet 或 YOLO
模型文件夹路径：/opt/yds/models/brain/...
```

blade Config 页面如存在：

```text
后端 API 地址：http://<jetson-ip>:8082/api
Python 检测服务：http://127.0.0.1:6522
模型文件夹路径：/opt/yds/models/blade
```

skin Config 页面如存在：

```text
后端 API 地址：http://<jetson-ip>:8083/api
Python 检测服务：http://127.0.0.1:2026
模型文件夹路径：/opt/yds/models/skin
```

---

## 11. 已确认决策与保留问题

### 11.1 已确认决策

1. 允许按项目启动，不要求 brain、blade、skin 三个项目同时在线。
2. blade 和 skin 不能共用同一份 Python 检测代码，应分别保留 `detect-api-blade.py` 和 `detect-api-skin.py`。
3. 启动 brain 项目时需要 YOLO、UNet、Mesh 常驻；不启动 brain 时，这三个 Python 服务可以关闭。
4. Jetson 当前 JetPack 版本暂不清楚，部署前保留检查项。
5. 模型最终使用 `.engine`，导出、验证和配置方法详见第 7 章。
6. 不迁移 PC 历史检测记录。
7. 前端访问方式同时给出两种：统一域名子路径访问、三个端口访问。
8. 不需要公网访问，只在局域网或设备本机访问。

### 11.2 按项目启动与关闭

执行位置：Jetson。

brain 项目启动：

```bash
sudo systemctl start mysql nginx
sudo systemctl start yds-brain
sudo systemctl start yds-brain-yolo yds-brain-unet yds-mesh
```

brain 项目关闭：

```bash
sudo systemctl stop yds-brain
sudo systemctl stop yds-brain-yolo yds-brain-unet yds-mesh
```

blade 项目启动：

```bash
sudo systemctl start mysql nginx
sudo systemctl start yds-blade
sudo systemctl start yds-blade-detect
```

blade 项目关闭：

```bash
sudo systemctl stop yds-blade
sudo systemctl stop yds-blade-detect
```

skin 项目启动：

```bash
sudo systemctl start mysql nginx
sudo systemctl start yds-skin
sudo systemctl start yds-skin-detect
```

skin 项目关闭：

```bash
sudo systemctl stop yds-skin
sudo systemctl stop yds-skin-detect
```

关闭全部项目服务，但保留 MySQL 和 Nginx：

```bash
sudo systemctl stop yds-brain yds-brain-yolo yds-brain-unet yds-mesh
sudo systemctl stop yds-blade yds-blade-detect
sudo systemctl stop yds-skin yds-skin-detect
```

如果不希望某个项目开机自启：

```bash
sudo systemctl disable yds-brain yds-brain-yolo yds-brain-unet yds-mesh
sudo systemctl disable yds-blade yds-blade-detect
sudo systemctl disable yds-skin yds-skin-detect
```

如果要“立即关闭并取消开机自启”：

```bash
sudo systemctl disable --now yds-brain yds-brain-yolo yds-brain-unet yds-mesh
sudo systemctl disable --now yds-blade yds-blade-detect
sudo systemctl disable --now yds-skin yds-skin-detect
```

查看当前哪些项目服务在线：

```bash
systemctl --no-pager --type=service --state=running | grep yds
```

### 11.3 推荐增加按项目 target

执行位置：Jetson。

为了把 brain 做成一个命令启动，可以创建：`/etc/systemd/system/yds-brain-stack.target`

```ini
[Unit]
Description=YDS Brain Project Stack
Wants=mysql.service nginx.service yds-brain.service yds-brain-yolo.service yds-brain-unet.service yds-mesh.service
After=network-online.target mysql.service nginx.service

[Install]
WantedBy=multi-user.target
```

blade：`/etc/systemd/system/yds-blade-stack.target`

```ini
[Unit]
Description=YDS Blade Project Stack
Wants=mysql.service nginx.service yds-blade.service yds-blade-detect.service
After=network-online.target mysql.service nginx.service

[Install]
WantedBy=multi-user.target
```

skin：`/etc/systemd/system/yds-skin-stack.target`

```ini
[Unit]
Description=YDS Skin Project Stack
Wants=mysql.service nginx.service yds-skin.service yds-skin-detect.service
After=network-online.target mysql.service nginx.service

[Install]
WantedBy=multi-user.target
```

启停命令：

```bash
sudo systemctl daemon-reload
sudo systemctl start yds-brain-stack.target
sudo systemctl stop yds-brain-stack.target

sudo systemctl start yds-blade-stack.target
sudo systemctl stop yds-blade-stack.target

sudo systemctl start yds-skin-stack.target
sudo systemctl stop yds-skin-stack.target
```

### 11.4 模型统一使用 engine

执行位置：Jetson。

本节只记录最终模型策略和路径，`.pt/.pth` 如何在 Jetson 本机导出 `.engine`、如何验证、如何写入 Config 页面，详见第 7 章。

推荐最终模型路径：

```text
/opt/yds/models/brain/yolo/best.engine
/opt/yds/models/brain/unet/best.engine
/opt/yds/models/blade/best.engine
/opt/yds/models/skin/best.engine
```

上线前必须执行：

```bash
trtexec --loadEngine=/opt/yds/models/brain/yolo/best.engine --shapes=images:1x3x640x640
```

如果是动态 shape 的 engine，要按实际模型输入名和 shape 调整 `--shapes`。更完整的验证流程见：

```text
docs/engine兼容性最小验证流程.md
```

### 11.5 不迁移 PC 历史检测记录

部署 Jetson 时数据库推荐只导出：

```text
结构
基础账号
系统配置
模型配置
必要字典数据
```

不导出：

```text
历史检测记录
历史上传文件记录
PC uploads 文件
PC results 文件
```

这样可以避免 Jetson 数据库里存在 Windows 文件路径，例如：

```text
D:\YDS\code\springb\uploads\...
```

### 11.6 方案 A：统一域名子路径访问

访问形式：

```text
http://<jetson-ip>/brain
http://<jetson-ip>/blade
http://<jetson-ip>/skin
```

适合：希望用户只记一个 IP 或一个域名。

#### 11.6.1 前端构建配置

执行位置：PC。

三个前端需要分别设置 base 路径。

brain：

```bash
cd D:\YDS\code\vue
npm run build
```

如果项目使用 Vite，需要在 `vue/vite.config.js` 配置：

```js
export default defineConfig({
  base: '/brain/'
})
```

blade：

```js
export default defineConfig({
  base: '/blade/'
})
```

skin：

```js
export default defineConfig({
  base: '/skin/'
})
```

如果项目使用 Vue CLI，则改 `vue.config.js`：

```js
module.exports = {
  publicPath: '/brain/'
}
```

blade、skin 分别改为 `/blade/`、`/skin/`。

#### 11.6.2 Nginx 配置

执行位置：Jetson。

配置文件：`/etc/nginx/sites-available/yds`

```nginx
server {
    listen 80;
    server_name _;

    location /brain/ {
        alias /var/www/yds/brain/;
        try_files $uri $uri/ /brain/index.html;
    }

    location /brain/api/ {
        proxy_pass http://127.0.0.1:9527/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location /blade/ {
        alias /var/www/yds/blade/;
        try_files $uri $uri/ /blade/index.html;
    }

    location /blade/api/ {
        proxy_pass http://127.0.0.1:1234/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location /skin/ {
        alias /var/www/yds/skin/;
        try_files $uri $uri/ /skin/index.html;
    }

    location /skin/api/ {
        proxy_pass http://127.0.0.1:1907/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

启用：

```bash
sudo ln -sf /etc/nginx/sites-available/yds /etc/nginx/sites-enabled/yds
sudo nginx -t
sudo systemctl reload nginx
```

#### 11.6.3 Web Config 页面配置

执行位置：浏览器。

```text
brain 后端 API 地址：http://<jetson-ip>/brain/api
blade 后端 API 地址：http://<jetson-ip>/blade/api
skin 后端 API 地址：http://<jetson-ip>/skin/api
```

#### 11.6.4 优劣势

优点：

- 用户只需要记一个 IP 或域名。
- 后续如果加 HTTPS，只需要给 80/443 配置一次。
- 路径统一，适合演示、内网部署和后期网关化。

缺点：

- 前端必须正确配置 base/publicPath。
- 如果代码里写死 `/api`、`/assets`、`/static`，需要逐项检查。
- Nginx 子路径反代配置更容易出错。

### 11.7 方案 B：三个端口访问

访问形式：

```text
http://<jetson-ip>:8081
http://<jetson-ip>:8082
http://<jetson-ip>:8083
```

适合：当前阶段快速部署、快速排错。

#### 11.7.1 前端构建配置

执行位置：PC。

三个前端可以保持默认 base：

```text
/
```

构建后分别上传：

```text
brain dist -> /var/www/yds/brain
blade dist -> /var/www/yds/blade
skin dist  -> /var/www/yds/skin
```

#### 11.7.2 Nginx 配置

执行位置：Jetson。

配置文件：`/etc/nginx/sites-available/yds`

```nginx
server {
    listen 8081;
    server_name _;
    root /var/www/yds/brain;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:9527/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}

server {
    listen 8082;
    server_name _;
    root /var/www/yds/blade;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:1234/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}

server {
    listen 8083;
    server_name _;
    root /var/www/yds/skin;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:1907/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

启用：

```bash
sudo ln -sf /etc/nginx/sites-available/yds /etc/nginx/sites-enabled/yds
sudo nginx -t
sudo systemctl reload nginx
```

#### 11.7.3 Web Config 页面配置

执行位置：浏览器。

```text
brain 后端 API 地址：http://<jetson-ip>:8081/api
blade 后端 API 地址：http://<jetson-ip>:8082/api
skin 后端 API 地址：http://<jetson-ip>:8083/api
```

#### 11.7.4 优劣势

优点：

- 最容易部署，前端通常不用改 base/publicPath。
- 三个项目互相隔离，排错直观。
- 当前阶段更稳，适合先在 Jetson 上跑通功能。

缺点：

- 用户要记三个端口。
- 如果后期需要 HTTPS 或统一入口，配置会分散。
- 防火墙需要开放多个端口。

### 11.8 不需要公网访问

执行位置：Jetson。

不做：

```text
公网端口映射
公网域名解析
公网 HTTPS 证书
外网开放 Python 服务端口
```

建议：

```bash
sudo ufw allow from 192.168.0.0/16 to any port 80 proto tcp
sudo ufw allow from 192.168.0.0/16 to any port 8081 proto tcp
sudo ufw allow from 192.168.0.0/16 to any port 8082 proto tcp
sudo ufw allow from 192.168.0.0/16 to any port 8083 proto tcp
sudo ufw deny 5001/tcp
sudo ufw deny 3408/tcp
sudo ufw deny 5002/tcp
sudo ufw deny 6522/tcp
sudo ufw deny 2026/tcp
```

如果只使用统一域名子路径方案，只需要开放 80；如果只使用三个端口方案，就开放 8081、8082、8083。

Python 服务建议只给后端本机调用，后端配置中使用：

```text
http://127.0.0.1:5001
http://127.0.0.1:3408
http://127.0.0.1:5002
http://127.0.0.1:6522
http://127.0.0.1:2026
```

### 11.9 保留问题

Jetson 当前 JetPack 版本仍需确认。确认命令：

```bash
cat /etc/nv_tegra_release
dpkg-query --show nvidia-l4t-core
python3 -c "import tensorrt as trt; print(trt.__version__)"
```

确认 JetPack 版本后，再确定 PyTorch、torchvision、TensorRT Python 调用方式和 `.engine` 导出方式；具体导出流程见第 7 章。

---

## 12. 官方参考

- NVIDIA Jetson Orin Nano Developer Kit User Guide: https://developer.nvidia.com/embedded/learn/jetson-orin-nano-devkit-user-guide/index.html
- NVIDIA Jetson Orin Nano Developer Kit Hardware Specs: https://developer.nvidia.com/embedded/learn/jetson-orin-nano-devkit-user-guide/hardware_spec.html
- NVIDIA Jetson Orin Nano Software Setup: https://developer.nvidia.com/embedded/learn/jetson-orin-nano-devkit-user-guide/software_setup.html
- NVIDIA Orin Nano Super 性能模式说明: https://developer.nvidia.com/blog/nvidia-jetson-orin-nano-developer-kit-gets-a-super-boost/
- Miniforge aarch64 安装包: https://github.com/conda-forge/miniforge
