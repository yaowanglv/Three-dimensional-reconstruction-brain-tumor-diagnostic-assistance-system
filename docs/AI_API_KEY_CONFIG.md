# AI大模型API Key配置指南

## 支持的AI模型

系统目前支持以下三种大模型：

| 模型 | 名称 | 官网 |
|------|------|------|
| DeepSeek | DeepSeek-V3/Chat | https://platform.deepseek.com |
| GLM | 智谱AI GLM-4 | https://open.bigmodel.cn |
| Kimi | Moonshot AI | https://platform.moonshot.cn |

---

## API Key配置方式

### 方式一：修改application.yml（适合开发环境）

编辑文件：`springb/src/main/resources/application.yml`

```yaml
ai:
  models:
    deepseek:
      api-key: sk-your-deepseek-api-key-here
      endpoint: https://api.deepseek.com/v1/chat/completions
      model-name: deepseek-chat
    glm:
      api-key: your-glm-api-key-here
      endpoint: https://open.bigmodel.cn/api/paas/v4/chat/completions
      model-name: glm-4
    kimi:
      api-key: your-kimi-api-key-here
      endpoint: https://api.moonshot.cn/v1/chat/completions
      model-name: moonshot-v1-8k
```

**注意**：这种方式会将API Key硬编码在代码中，不推荐用于生产环境。

---

### 方式二：环境变量（推荐）

在启动应用前设置环境变量：

**Windows (PowerShell)**:
```powershell
$env:DEEPSEEK_API_KEY="sk-your-deepseek-api-key"
$env:GLM_API_KEY="your-glm-api-key"
$env:KIMI_API_KEY="your-kimi-api-key"
```

**Windows (CMD)**:
```cmd
set DEEPSEEK_API_KEY=sk-your-deepseek-api-key
set GLM_API_KEY=your-glm-api-key
set KIMI_API_KEY=your-kimi-api-key
```

**Linux/Mac**:
```bash
export DEEPSEEK_API_KEY="sk-your-deepseek-api-key"
export GLM_API_KEY="your-glm-api-key"
export KIMI_API_KEY="your-kimi-api-key"
```

---

### 方式三：IDEA运行配置

如果你使用IntelliJ IDEA运行项目：

1. 点击顶部菜单 `Run` → `Edit Configurations...`
2. 找到你的Spring Boot启动配置
3. 在 `Environment variables` 中添加：
   ```
   DEEPSEEK_API_KEY=sk-your-deepseek-api-key;GLM_API_KEY=your-glm-api-key;KIMI_API_KEY=your-kimi-api-key
   ```
4. 点击 `Apply` → `OK`

---

## 获取API Key

### 1. DeepSeek

1. 访问 https://platform.deepseek.com
2. 注册/登录账号
3. 进入「API Keys」页面
4. 点击「创建API Key」
5. 复制生成的Key（格式：`sk-...`）

### 2. 智谱GLM

1. 访问 https://open.bigmodel.cn
2. 注册/登录账号
3. 进入「个人中心」→「API Keys」
4. 添加新的API Key
5. 复制生成的Key

### 3. Kimi (Moonshot)

1. 访问 https://platform.moonshot.cn
2. 注册/登录账号
3. 进入「账户管理」→「API Key管理」
4. 点击「创建API Key」
5. 复制生成的Key（格式：`sk-...`）

**可选模型**：
- `moonshot-v1-8k`（默认，适合短文本）
- `moonshot-v1-32k`（长文本）
- `moonshot-v1-128k`（超长文本）

---

## 验证配置

启动应用后，可以调用接口检查AI模型配置状态：

```bash
GET http://localhost:9527/detect/aiConfigStatus
```

返回示例：
```json
{
  "code": "200",
  "data": {
    "deepseek": true,
    "glm": false,
    "kimi": true
  },
  "msg": "操作成功"
}
```

- `true`: 该模型已配置API Key，可以使用
- `false`: 该模型未配置API Key，无法使用

---

## 前端使用说明

在前端页面 `http://localhost:4000/manager/detect`：

1. 完成图像检测
2. 在「AI辅助分析」区域选择已配置的模型
3. 点击「AI辅助分析」按钮
4. 等待分析结果

如果选择的模型未配置API Key，系统会提示：
> "AI模型 'xxx' 未配置API Key，请联系管理员配置"

---

## 费用说明

各模型API调用会产生费用，请参考官方定价：

- DeepSeek: https://platform.deepseek.com/pricing
- 智谱GLM: https://open.bigmodel.cn/pricing
- Kimi: https://platform.moonshot.cn/docs/pricing

建议开启额度监控，避免超出预算。
