# Detect.vue 开发检查清单

> 按照此清单逐步完成开发，适用于 Vibe Coding 模式

---

## 📋 环境准备

- [ ] 确认MySQL数据库已启动
- [ ] 确认SpringBoot项目可正常运行（端口9527）
- [ ] 确认Vue项目可正常运行
- [ ] 确认Element Plus图标库已安装

---

## 🔧 Phase 1: 数据库与后端基础 (预计2-4小时)

### 1.1 创建数据库表
```sql
-- 在MySQL中执行以下SQL
CREATE TABLE `detect` (
    `id` INT PRIMARY KEY AUTO_INCREMENT COMMENT '检测记录ID',
    `user_id` INT NOT NULL COMMENT '操作用户ID',
    `user_name` VARCHAR(50) COMMENT '操作用户名',
    `original_image_name` VARCHAR(255) COMMENT '原始图像文件名',
    `original_image_url` VARCHAR(500) NOT NULL COMMENT '原始图像存储路径',
    `original_image_size` BIGINT COMMENT '原始图像大小',
    `original_image_format` VARCHAR(10) COMMENT '原始图像格式',
    `result_image_url` VARCHAR(500) COMMENT '检测结果图像路径',
    `detection_data` JSON COMMENT '检测结果JSON数据',
    `ai_model` VARCHAR(50) COMMENT '使用的大模型',
    `ai_analysis_result` TEXT COMMENT 'AI分析结果',
    `ai_analysis_time` DATETIME COMMENT 'AI分析时间',
    `detect_status` TINYINT DEFAULT 0 COMMENT '检测状态',
    `ai_status` TINYINT DEFAULT 0 COMMENT 'AI分析状态',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `remark` VARCHAR(500) COMMENT '备注'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='脑肿瘤检测记录表';
```

- [ ] SQL执行成功
- [ ] 表结构检查无误

### 1.2 创建后端文件

| 文件路径 | 状态 |
|---------|------|
| `springb/src/main/java/com/example/springb/entity/Detect.java` | [ ] |
| `springb/src/main/java/com/example/springb/mapper/DetectMapper.java` | [ ] |
| `springb/src/main/resources/mapper/DetectMapper.xml` | [ ] |
| `springb/src/main/java/com/example/springb/service/DetectService.java` | [ ] |
| `springb/src/main/java/com/example/springb/controller/DetectController.java` | [ ] |

### 1.3 配置文件更新

**文件**: `springb/src/main/resources/application.yml`

需要添加的内容：
```yaml
spring:
  servlet:
    multipart:
      enabled: true
      max-file-size: 50MB
      max-request-size: 100MB

file:
  upload:
    path: uploads/
  access:
    url: http://localhost:9527/files/

python:
  detect:
    url: http://localhost:5000/detect

ai:
  models:
    deepseek:
      api-key: ${DEEPSEEK_API_KEY:your_key}
      endpoint: https://api.deepseek.com/v1/chat/completions
      model-name: deepseek-chat
    glm:
      api-key: ${GLM_API_KEY:your_key}
      endpoint: https://open.bigmodel.cn/api/paas/v4/chat/completions
      model-name: glm-4
    doubao:
      api-key: ${DOUBAO_API_KEY:your_key}
      endpoint: https://ark.cn-beijing.volces.com/api/v3/chat/completions
      model-name: doubao-pro-32k
```

- [ ] application.yml 更新完成
- [ ] SpringBoot项目重新编译运行
- [ ] 测试接口是否正常（可用Postman测试/upload接口）

---

## 🎨 Phase 2: 前端开发 (预计3-5小时)

### 2.1 路由配置

**文件**: `vue/src/router/index.js`

在children数组中添加：
```javascript
{path: 'detect', meta: {name: '脑肿瘤检测分析'}, component: () => import('../views/Detect.vue')},
```

- [ ] 路由配置完成

### 2.2 Manager.vue菜单更新

**文件**: `vue/src/views/Manager.vue`

1. 导入图标：
```javascript
import { EditPen, FirstAidKit } from '@element-plus/icons-vue'
```

2. 添加菜单项（在el-menu中）：
```vue
<el-sub-menu index="3">
  <template #title>
    <el-icon><FirstAidKit /></el-icon>
    <span style="font-size: 15px">智能诊断</span>
  </template>
  <el-menu-item index="/manager/detect">脑肿瘤检测分析</el-menu-item>
</el-sub-menu>
```

- [ ] Manager.vue 更新完成
- [ ] 菜单显示正常

### 2.3 创建Detect.vue

**文件**: `vue/src/views/Detect.vue`

- [ ] 创建文件
- [ ] 复制完整代码
- [ ] 检查无语法错误
- [ ] 页面能正常访问 `/manager/detect`

### 2.4 安装图标库（如需要）

```bash
cd vue
npm install @element-plus/icons-vue
```

- [ ] 图标库安装完成

---

## 🔗 Phase 3: Python检测服务对接 (预计2-4小时)

### 3.1 Python服务开发（如已有可跳过）

参考接口规范：
```python
from flask import Flask, request, jsonify
import cv2
import numpy as np

app = Flask(__name__)

@app.route('/detect', methods=['POST'])
def detect():
    data = request.json
    image_path = data.get('imagePath')
    record_id = data.get('recordId')
    
    # TODO: 加载深度学习模型进行检测
    # model = load_model()
    # results = model.predict(image_path)
    
    return jsonify({
        "code": 200,
        "message": "success",
        "data": {
            "recordId": record_id,
            "tumorDetected": True,
            "tumorType": "glioma",
            "confidence": 0.94,
            "boxes": [...],
            "resultImagePath": "/path/to/result.jpg"
        }
    })

if __name__ == '__main__':
    app.run(port=5000)
```

- [ ] Python服务运行正常（端口5000）

### 3.2 Java端调用实现

在DetectController中实现startDetect方法：
```java
// 使用RestTemplate调用Python服务
RestTemplate restTemplate = new RestTemplate();
Map<String, Object> requestBody = new HashMap<>();
requestBody.put("recordId", id);
requestBody.put("imagePath", fullImagePath);

ResponseEntity<Map> response = restTemplate.postForEntity(
    pythonDetectUrl, 
    requestBody, 
    Map.class
);
```

- [ ] Java调用Python服务成功
- [ ] 检测结果正确保存到数据库

---

## 🤖 Phase 4: AI大模型对接 (预计2-4小时)

### 4.1 创建AI服务类

**文件**: `springb/src/main/java/com/example/springb/service/AiService.java`

```java
@Service
public class AiService {
    
    @Value("${ai.models.deepseek.api-key}")
    private String deepseekKey;
    
    public String callDeepSeek(String prompt) {
        // 实现HTTP调用
    }
    
    public String callGLM(String prompt) {
        // 实现HTTP调用
    }
    
    public String callDoubao(String prompt) {
        // 实现HTTP调用
    }
}
```

### 4.2 配置API密钥

三种方式（推荐方式1）：
1. 环境变量：`DEEPSEEK_API_KEY=xxx`
2. 启动参数：`-Ddeepseek.api.key=xxx`
3. 直接修改application.yml（不推荐生产环境）

- [ ] API密钥配置完成
- [ ] AI服务调用测试成功

---

## 🧪 Phase 5: 测试验证

### 5.1 功能测试

| 测试项 | 操作步骤 | 预期结果 | 状态 |
|--------|----------|----------|------|
| 图像上传 | 点击上传按钮，选择图片 | 上传成功，显示原图 | [ ] |
| 开始检测 | 点击开始检测 | 显示检测中，完成后显示结果图 | [ ] |
| AI分析 | 选择模型，点击分析 | 显示分析中，完成后显示报告 | [ ] |
| 历史记录 | 查看下方列表 | 显示历史记录，支持分页 | [ ] |
| 查看详情 | 点击历史记录查看按钮 | 加载到上方对比区域 | [ ] |
| 删除记录 | 点击删除，确认 | 记录删除，列表刷新 | [ ] |

### 5.2 异常测试

| 测试项 | 操作步骤 | 预期结果 | 状态 |
|--------|----------|----------|------|
| 上传大文件 | 上传超过50MB文件 | 提示文件过大 | [ ] |
| 上传非图片 | 上传txt文件 | 提示格式错误 | [ ] |
| 重复检测 | 检测中再次点击检测 | 按钮禁用，不可重复点击 | [ ] |
| AI分析未检测 | 未检测点击AI分析 | 提示先完成检测 | [ ] |

### 5.3 性能测试

| 测试项 | 指标 | 状态 |
|--------|------|------|
| 页面加载 | < 2秒 | [ ] |
| 图像上传 | < 10秒(10MB) | [ ] |
| 检测响应 | < 30秒 | [ ] |
| AI分析 | < 20秒 | [ ] |

---

## 🚀 部署上线

### 6.1 生产环境配置

- [ ] 修改数据库连接配置
- [ ] 修改文件上传路径为绝对路径
- [ ] 配置生产环境API密钥
- [ ] 配置Python服务生产地址
- [ ] 开启日志记录

### 6.2 文件目录准备

```
# 创建上传目录
mkdir -p /data/uploads/detect
chmod 755 /data/uploads

# 创建日志目录
mkdir -p /data/logs
```

---

## 📝 常见问题排查

### Q1: 图像上传失败
- 检查application.yml中的文件上传配置
- 检查上传目录是否有写权限
- 检查文件大小限制

### Q2: Python服务调用失败
- 确认Python服务已启动（端口5000）
- 检查防火墙设置
- 查看Java端错误日志

### Q3: AI分析无响应
- 检查API密钥是否正确
- 检查网络是否能访问AI服务商
- 查看超时设置（默认30秒可能不够）

### Q4: 页面空白或报错
- 检查浏览器控制台错误
- 确认Element Plus图标库已安装
- 检查Vue路由配置

---

## ✅ 完成标志

当以下所有项都完成时，开发完成：

- [ ] 用户可以成功上传医学影像
- [ ] 点击检测后能看到检测结果图
- [ ] 可以选择不同模型进行AI分析
- [ ] AI分析结果正确显示
- [ ] 历史记录能正确显示和操作
- [ ] 所有测试用例通过

---

*最后更新: 2026-04-20*
