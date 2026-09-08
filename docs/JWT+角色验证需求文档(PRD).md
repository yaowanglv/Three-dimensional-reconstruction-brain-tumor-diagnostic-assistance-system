# JWT + 角色验证需求文档 (PRD)

## 文档信息

| 项目 | 内容 |
|------|------|
| 项目名称 | 大模型驱动的脑肿瘤智能辅助诊断与分析系统 |
| 文档版本 | V1.0 |
| 创建日期 | 2026-05-17 |
| 文档状态 | 草稿 |

---

## 1. 项目概述

### 1.1 背景

脑肿瘤智能辅助诊断与分析系统是一个基于大模型的医疗影像分析平台，涉及敏感的医疗数据和AI诊断结果。当前系统的认证机制较为简单，存在以下问题：

- 用户凭证以明文形式存储在localStorage中，安全性不足
- 缺乏token过期和刷新机制，会话管理不完善
- 前端路由守卫仅基于本地存储的角色判断，容易被绕过
- 后端接口缺乏统一的权限验证机制

### 1.2 目标

实现基于JWT（JSON Web Token）的身份认证和基于角色的访问控制（RBAC），提升系统安全性和可维护性。

### 1.3 范围

- 后端：Spring Boot集成JWT认证
- 前端：Vue 3集成JWT token管理
- 数据库：用户表和角色表设计

---

## 2. 用户角色定义

### 2.1 角色列表

| 角色标识 | 角色名称 | 描述 |
|----------|----------|------|
| `admin` | 系统管理员 | 拥有系统所有权限，包括用户管理、系统配置等 |
| `doctor` | 医生 | 可以进行脑肿瘤诊断、查看患者数据、生成报告 |
| `researcher` | 研究人员 | 可以查看统计数据、导出数据，但不能进行诊断操作 |
| `user` | 普通用户 | 基础权限，仅能查看公开数据和自己的信息 |

### 2.2 权限矩阵

| 功能模块 | admin | doctor | researcher | user |
|----------|-------|--------|------------|------|
| 用户管理 | ✅ | ❌ | ❌ | ❌ |
| 脑肿瘤诊断 | ✅ | ✅ | ❌ | ❌ |
| 诊断历史查看 | ✅ | ✅ | ✅ | 仅自己 |
| 数据可视化 | ✅ | ✅ | ✅ | ✅ |
| 数据导出 | ✅ | ✅ | ✅ | ❌ |
| 系统配置 | ✅ | ❌ | ❌ | ❌ |

---

## 3. 功能需求

### 3.1 用户登录

#### 3.1.1 登录流程

1. 用户输入用户名和密码
2. 前端将凭证发送到后端 `/api/auth/login`
3. 后端验证凭证，生成JWT token对
4. 前端存储token并跳转到主页

#### 3.1.2 Token设计

**Access Token（访问令牌）**
- 有效期：2小时
- 包含信息：用户ID、用户名、角色、权限列表
- 用途：API请求的身份验证

**Refresh Token（刷新令牌）**
- 有效期：7天
- 包含信息：用户ID、token类型
- 用途：刷新Access Token

#### 3.1.3 接口设计

**登录接口**
```
POST /api/auth/login
Request:
{
    "username": "string",
    "password": "string"
}

Response:
{
    "code": 200,
    "data": {
        "accessToken": "string",
        "refreshToken": "string",
        "userInfo": {
            "id": 1,
            "username": "admin",
            "name": "管理员",
            "role": "admin"
        }
    },
    "msg": "登录成功"
}
```

**刷新Token接口**
```
POST /api/auth/refresh
Request:
{
    "refreshToken": "string"
}

Response:
{
    "code": 200,
    "data": {
        "accessToken": "string",
        "refreshToken": "string"
    },
    "msg": "刷新成功"
}
```

**登出接口**
```
POST /api/auth/logout
Headers:
    Authorization: Bearer <accessToken>

Response:
{
    "code": 200,
    "msg": "登出成功"
}
```

**获取用户信息接口**
```
GET /api/auth/userInfo
Headers:
    Authorization: Bearer <accessToken>

Response:
{
    "code": 200,
    "data": {
        "id": 1,
        "username": "admin",
        "name": "管理员",
        "role": "admin",
        "permissions": ["user:manage", "detect:create", "detect:view", ...]
    },
    "msg": "获取成功"
}
```

### 3.2 Token管理

#### 3.2.1 Token存储

**前端存储**
- Access Token：存储在内存中（Vue reactive state）
- Refresh Token：存储在httpOnly cookie中（防止XSS攻击）

**后端存储**
- Refresh Token：存储在数据库中，支持主动失效
- Token黑名单：用于登出后的token失效

#### 3.2.2 Token刷新机制

1. 前端拦截器检查Access Token是否即将过期（剩余时间<5分钟）
2. 如果即将过期，自动调用刷新接口
3. 如果Refresh Token也过期，跳转到登录页面
4. 支持并发请求时的token刷新队列

#### 3.2.3 Token失效场景

- 用户主动登出
- Refresh Token过期
- 用户密码被修改
- 管理员强制用户下线

### 3.3 权限控制

#### 3.3.1 后端权限控制

**接口级别权限**
- 使用Spring Security注解控制接口访问权限
- 支持角色验证和权限验证

**数据级别权限**
- 普通用户只能查看自己的数据
- 医生可以查看自己诊断的患者数据
- 管理员可以查看所有数据

#### 3.3.2 前端权限控制

**路由级别权限**
- 根据用户角色动态生成可访问路由
- 无权限时跳转到403页面

**按钮级别权限**
- 使用自定义指令控制按钮显示/隐藏
- 支持角色和权限两种验证方式

**菜单级别权限**
- 根据用户角色动态生成菜单
- 无权限菜单不显示

---

## 4. 非功能需求

### 4.1 安全性

1. **密码加密**：使用BCrypt算法加密存储密码
2. **HTTPS**：生产环境必须使用HTTPS
3. **CORS配置**：限制允许的域名
4. **请求签名**：关键接口支持请求签名验证
5. **防重放攻击**：Token包含唯一标识，支持防重放

### 4.2 性能

1. **Token验证响应时间**：<50ms
2. **并发支持**：支持1000+并发用户
3. **缓存策略**：用户权限信息缓存，减少数据库查询

### 4.3 可用性

1. **无感刷新**：用户无感知的Token刷新
2. **会话保持**：支持"记住我"功能
3. **多端登录**：支持同一账号多端登录（可配置）

### 4.4 可维护性

1. **配置化**：Token有效期、密钥等参数可配置
2. **日志记录**：登录、登出、权限验证等操作日志
3. **监控告警**：异常登录行为监控

---

## 5. 数据库设计

### 5.1 用户表（admin）

```sql
CREATE TABLE `admin` (
    `id` INT PRIMARY KEY AUTO_INCREMENT,
    `username` VARCHAR(50) NOT NULL UNIQUE,
    `password` VARCHAR(100) NOT NULL,
    `name` VARCHAR(50),
    `role` VARCHAR(20) DEFAULT 'user',
    `email` VARCHAR(100),
    `phone` VARCHAR(20),
    `status` TINYINT DEFAULT 1 COMMENT '0-禁用 1-启用',
    `last_login_time` DATETIME,
    `last_login_ip` VARCHAR(50),
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_username` (`username`),
    INDEX `idx_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 5.2 刷新Token表（refresh_token）

```sql
CREATE TABLE `refresh_token` (
    `id` INT PRIMARY KEY AUTO_INCREMENT,
    `user_id` INT NOT NULL,
    `token` VARCHAR(500) NOT NULL,
    `device_info` VARCHAR(200),
    `ip_address` VARCHAR(50),
    `expire_time` DATETIME NOT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_token` (`token`(100)),
    FOREIGN KEY (`user_id`) REFERENCES `admin`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 5.3 操作日志表（operation_log）

```sql
CREATE TABLE `operation_log` (
    `id` INT PRIMARY KEY AUTO_INCREMENT,
    `user_id` INT,
    `username` VARCHAR(50),
    `operation` VARCHAR(50) COMMENT '操作类型：login/logout/access',
    `method` VARCHAR(10),
    `url` VARCHAR(200),
    `ip` VARCHAR(50),
    `status` TINYINT COMMENT '0-失败 1-成功',
    `error_msg` TEXT,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 6. 接口安全规范

### 6.1 请求头规范

```
Authorization: Bearer <accessToken>
Content-Type: application/json
```

### 6.2 错误码定义

| 错误码 | 描述 | 处理方式 |
|--------|------|----------|
| 401 | 未认证 | 跳转登录页 |
| 401001 | Token过期 | 尝试刷新Token |
| 401002 | Token无效 | 跳转登录页 |
| 401003 | Refresh Token过期 | 跳转登录页 |
| 403 | 无权限 | 显示403页面 |
| 403001 | 角色权限不足 | 显示权限不足提示 |

### 6.3 响应格式

```json
{
    "code": 200,
    "data": {},
    "msg": "success",
    "timestamp": 1715923200000
}
```

---

## 7. 测试用例

### 7.1 登录测试

| 测试项 | 输入 | 预期结果 |
|--------|------|----------|
| 正确凭证登录 | 正确用户名/密码 | 返回token，登录成功 |
| 错误密码登录 | 正确用户名/错误密码 | 返回错误提示 |
| 不存在用户 | 不存在的用户名 | 返回错误提示 |
| 禁用账号登录 | 禁用状态的账号 | 返回账号已禁用提示 |
| 并发登录 | 同一账号多地登录 | 根据配置策略处理 |

### 7.2 Token测试

| 测试项 | 输入 | 预期结果 |
|--------|------|----------|
| 有效Token访问 | 有效的Access Token | 正常访问 |
| 过期Token访问 | 过期的Access Token | 返回401001 |
| 无效Token访问 | 伪造的Token | 返回401002 |
| 刷新Token | 有效的Refresh Token | 返回新Token对 |
| 过期Refresh Token | 过期的Refresh Token | 返回401003 |

### 7.3 权限测试

| 测试项 | 输入 | 预期结果 |
|--------|------|----------|
| admin访问管理接口 | admin角色 | 允许访问 |
| user访问管理接口 | user角色 | 返回403 |
| doctor访问诊断接口 | doctor角色 | 允许访问 |
| researcher访问诊断接口 | researcher角色 | 返回403 |

---

## 8. 迁移方案

### 8.1 迁移步骤

1. **数据库迁移**：执行SQL脚本，更新表结构
2. **后端部署**：部署新版本后端代码
3. **前端部署**：部署新版本前端代码
4. **用户通知**：通知用户重新登录

### 8.2 兼容性处理

- 旧版本token在迁移期间仍然有效（2小时）
- 提供平滑过渡期，新旧接口并存

---

## 9. 风险评估

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|----------|
| 密钥泄露 | 高 | 低 | 定期轮换密钥，使用环境变量 |
| Token被盗用 | 中 | 中 | 短有效期，支持主动失效 |
| 性能下降 | 中 | 低 | 缓存优化，性能测试 |
| 用户体验下降 | 低 | 低 | 无感刷新，友好提示 |

---

## 10. 附录

### 10.1 术语表

| 术语 | 说明 |
|------|------|
| JWT | JSON Web Token，一种开放标准 |
| RBAC | Role-Based Access Control，基于角色的访问控制 |
| Access Token | 访问令牌，用于API认证 |
| Refresh Token | 刷新令牌，用于获取新的Access Token |

### 10.2 参考文档

- [JWT官网](https://jwt.io/)
- [Spring Security官方文档](https://spring.io/projects/spring-security)
- [Vue Router官方文档](https://router.vuejs.org/)
