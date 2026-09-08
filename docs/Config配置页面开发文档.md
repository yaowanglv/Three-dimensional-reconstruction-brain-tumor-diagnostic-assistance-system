# Config 配置页面开发文档

## 一、文档信息

| 项目 | 内容 |
|------|------|
| 文档名称 | Config 配置页面开发文档 |
| 版本 | v1.1 |
| 创建日期 | 2026-05-20 |
| 修改日期 | 2026-05-20 |
| 作者 | 蓝鸢尾 |
| 项目名称 | 脑诊智析——大模型驱动的脑肿瘤智能辅助诊断与分析系统 |

---

## 二、技术架构

### 2.1 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue 3 | 3.5 | 前端框架 |
| Element Plus | 2.10 | UI 组件库 |
| Vue Router | 4.5 | 路由管理 |
| Vite | - | 构建工具 |
| Spring Boot | 3.x | 后端框架 |
| MySQL | 8.x | 数据库 |

### 2.2 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                      Config.vue (配置页面)                   │
│                      （仅管理员可访问）                       │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │ 快速诊断配置 │  │ 精准分割配置 │  │ 批量分割配置 │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
│                          │                                  │
│                          ▼                                  │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                  config.js (前端工具模块)             │   │
│  │  - fetchConfig() / saveConfig()                     │   │
│  │  - fetchModels() / addModelFolder() / removeModel() │   │
│  │  - scanModels()                                     │   │
│  └─────────────────────────────────────────────────────┘   │
│                          │                                  │
│                          ▼                                  │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                后端 API (ConfigController)            │   │
│  │  - GET /api/config/models                           │   │
│  │  - POST /api/config/models/folder                   │   │
│  │  - DELETE /api/config/models/{id}                   │   │
│  │  - POST /api/config/models/scan                     │   │
│  │  - GET /api/config/thresholds                       │   │
│  │  - PUT /api/config/thresholds                       │   │
│  └─────────────────────────────────────────────────────┘   │
│                          │                                  │
│                          ▼                                  │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                   MySQL 数据库                        │   │
│  │  - model_config 表（模型配置）                        │   │
│  │  - system_config 表（系统配置）                       │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    其他页面 (Detect/Mask/Multi)              │
│  - 从后端 API 读取模型列表和阈值配置                        │
│  - 普通用户只能从下拉列表选择模型（禁止手动输入）            │
└─────────────────────────────────────────────────────────────┘
```

---

## 三、文件结构

### 3.1 新增文件

```
springb/src/main/java/com/example/springb/
├── controller/
│   └── ConfigController.java      # 配置管理接口（新建）
├── service/
│   └── ConfigService.java         # 配置业务逻辑（新建）
├── mapper/
│   └── ConfigMapper.java          # 数据库操作（新建）
├── entity/
│   ├── ModelConfig.java           # 模型配置实体（新建）
│   └── SystemConfig.java          # 系统配置实体（新建）
└── config/
    └── ModelScanner.java          # 模型文件扫描器（新建）

vue/src/
├── utils/
│   └── config.js                  # 前端配置管理工具（新建）
└── views/
    └── Config.vue                 # 配置页面主组件（新建）
```

### 3.2 修改文件

```
springb/src/main/java/com/example/springb/
├── config/
│   └── SecurityConfig.java        # 添加 /api/config/** 权限配置（修改）
└── controller/
    └── DetectController.java      # 修改模型加载逻辑（修改）

vue/src/
├── router/
│   └── index.js                   # 添加路由，配置 admin 权限（修改）
└── views/
    ├── Manager.vue                # 添加侧边栏菜单项（仅admin可见）（修改）
    ├── Detect.vue                 # 集成配置读取，模型下拉只读（修改）
    ├── Mask.vue                   # 集成配置读取，模型下拉只读（修改）
    └── Multi.vue                  # 集成配置读取，模型下拉只读（修改）
```

---

## 四、数据库设计

### 4.1 模型配置表

```sql
CREATE TABLE model_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    page_type VARCHAR(20) NOT NULL COMMENT '页面类型：detect/mask/multi',
    folder_path VARCHAR(500) NOT NULL COMMENT '模型文件夹路径',
    model_name VARCHAR(200) NOT NULL COMMENT '模型文件名',
    model_path VARCHAR(500) NOT NULL COMMENT '模型完整路径',
    model_type VARCHAR(10) NOT NULL COMMENT '模型类型：pt/pth/onnx',
    is_active TINYINT DEFAULT 1 COMMENT '是否启用：0-禁用 1-启用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_page_type (page_type),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型配置表';
```

### 4.2 系统配置表

```sql
CREATE TABLE system_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    config_key VARCHAR(50) NOT NULL UNIQUE COMMENT '配置键',
    config_value VARCHAR(500) NOT NULL COMMENT '配置值',
    description VARCHAR(200) COMMENT '配置说明',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- 初始化默认配置
INSERT INTO system_config (config_key, config_value, description) VALUES
('threshold.detect', '0.45', '快速诊断页面默认阈值'),
('threshold.mask', '0.45', '精准分割页面默认阈值'),
('threshold.multi', '0.25', '批量分割页面默认阈值'),
('global.backend_url', 'http://localhost:9527', '后端API地址'),
('global.python_detect_url', 'http://localhost:5001', 'Python检测服务地址'),
('global.python_segment_url', 'http://localhost:5001', 'Python分割服务地址');
```

---

## 五、后端实现

### 5.1 ModelConfig.java（实体类）

```java
package com.example.springb.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ModelConfig {
    private Long id;
    private String pageType;
    private String folderPath;
    private String modelName;
    private String modelPath;
    private String modelType;
    private Integer isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### 5.2 ConfigController.java（控制器）

```java
package com.example.springb.controller;

import com.example.springb.common.Result;
import com.example.springb.entity.ModelConfig;
import com.example.springb.service.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    @Autowired
    private ConfigService configService;

    /**
     * 获取指定页面的模型列表
     */
    @GetMapping("/models")
    public Result getModels(@RequestParam String pageType) {
        List<ModelConfig> models = configService.getModels(pageType);
        return Result.success(models);
    }

    /**
     * 添加模型文件夹并扫描
     */
    @PostMapping("/models/folder")
    @PreAuthorize("hasRole('admin')")
    public Result addModelFolder(@RequestBody Map<String, String> request) {
        String pageType = request.get("pageType");
        String folderPath = request.get("folderPath");
        
        if (pageType == null || folderPath == null) {
            return Result.error("参数不能为空");
        }
        
        List<ModelConfig> models = configService.scanAndSaveModels(pageType, folderPath);
        return Result.success(models);
    }

    /**
     * 删除模型配置（仅删除数据库记录，不删除物理文件）
     */
    @DeleteMapping("/models/{id}")
    @PreAuthorize("hasRole('admin')")
    public Result deleteModel(@PathVariable Long id) {
        configService.deleteModel(id);
        return Result.success();
    }

    /**
     * 重新扫描模型文件夹
     */
    @PostMapping("/models/scan")
    @PreAuthorize("hasRole('admin')")
    public Result rescanModels(@RequestBody Map<String, String> request) {
        String pageType = request.get("pageType");
        String folderPath = request.get("folderPath");
        
        List<ModelConfig> models = configService.rescanModels(pageType, folderPath);
        return Result.success(models);
    }

    /**
     * 获取阈值配置
     */
    @GetMapping("/thresholds")
    public Result getThresholds() {
        Map<String, Double> thresholds = configService.getThresholds();
        return Result.success(thresholds);
    }

    /**
     * 更新阈值配置
     */
    @PutMapping("/thresholds")
    @PreAuthorize("hasRole('admin')")
    public Result updateThresholds(@RequestBody Map<String, Double> thresholds) {
        configService.updateThresholds(thresholds);
        return Result.success();
    }

    /**
     * 获取全局配置
     */
    @GetMapping("/global")
    public Result getGlobalConfig() {
        Map<String, String> config = configService.getGlobalConfig();
        return Result.success(config);
    }

    /**
     * 更新全局配置
     */
    @PutMapping("/global")
    @PreAuthorize("hasRole('admin')")
    public Result updateGlobalConfig(@RequestBody Map<String, String> config) {
        configService.updateGlobalConfig(config);
        return Result.success();
    }
}
```

### 5.3 ConfigService.java（服务层）

```java
package com.example.springb.service;

import com.example.springb.entity.ModelConfig;
import com.example.springb.mapper.ConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;

@Service
public class ConfigService {

    @Autowired
    private ConfigMapper configMapper;

    private static final Set<String> MODEL_EXTENSIONS = Set.of("pt", "pth", "onnx");

    public List<ModelConfig> getModels(String pageType) {
        return configMapper.selectByPageType(pageType);
    }

    public List<ModelConfig> scanAndSaveModels(String pageType, String folderPath) {
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            throw new RuntimeException("目录不存在: " + folderPath);
        }

        List<ModelConfig> newModels = new ArrayList<>();
        scanDirectory(folder, pageType, folderPath, newModels);

        // 保存到数据库
        for (ModelConfig model : newModels) {
            // 检查是否已存在
            ModelConfig existing = configMapper.selectByPath(model.getModelPath());
            if (existing == null) {
                configMapper.insert(model);
            }
        }

        return getModels(pageType);
    }

    private void scanDirectory(File dir, String pageType, String baseFolder, List<ModelConfig> models) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, pageType, baseFolder, models);
            } else {
                String ext = getExtension(file.getName()).toLowerCase();
                if (MODEL_EXTENSIONS.contains(ext)) {
                    ModelConfig model = new ModelConfig();
                    model.setPageType(pageType);
                    model.setFolderPath(baseFolder);
                    model.setModelName(file.getName());
                    model.setModelPath(file.getAbsolutePath());
                    model.setModelType(ext);
                    model.setIsActive(1);
                    models.add(model);
                }
            }
        }
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "";
    }

    public void deleteModel(Long id) {
        // 仅删除数据库记录，不删除物理文件
        configMapper.deleteById(id);
    }

    public List<ModelConfig> rescanModels(String pageType, String folderPath) {
        // 先删除该页面该文件夹下的所有记录
        configMapper.deleteByPageTypeAndFolder(pageType, folderPath);
        // 重新扫描
        return scanAndSaveModels(pageType, folderPath);
    }

    public Map<String, Double> getThresholds() {
        Map<String, Double> thresholds = new HashMap<>();
        thresholds.put("detect", getDoubleConfig("threshold.detect", 0.45));
        thresholds.put("mask", getDoubleConfig("threshold.mask", 0.45));
        thresholds.put("multi", getDoubleConfig("threshold.multi", 0.25));
        return thresholds;
    }

    public void updateThresholds(Map<String, Double> thresholds) {
        if (thresholds.containsKey("detect")) {
            updateConfig("threshold.detect", String.valueOf(thresholds.get("detect")));
        }
        if (thresholds.containsKey("mask")) {
            updateConfig("threshold.mask", String.valueOf(thresholds.get("mask")));
        }
        if (thresholds.containsKey("multi")) {
            updateConfig("threshold.multi", String.valueOf(thresholds.get("multi")));
        }
    }

    public Map<String, String> getGlobalConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("backendUrl", getStringConfig("global.backend_url", "http://localhost:9527"));
        config.put("pythonDetectUrl", getStringConfig("global.python_detect_url", "http://localhost:5001"));
        config.put("pythonSegmentUrl", getStringConfig("global.python_segment_url", "http://localhost:5001"));
        return config;
    }

    public void updateGlobalConfig(Map<String, String> config) {
        if (config.containsKey("backendUrl")) {
            updateConfig("global.backend_url", config.get("backendUrl"));
        }
        if (config.containsKey("pythonDetectUrl")) {
            updateConfig("global.python_detect_url", config.get("pythonDetectUrl"));
        }
        if (config.containsKey("pythonSegmentUrl")) {
            updateConfig("global.python_segment_url", config.get("pythonSegmentUrl"));
        }
    }

    private Double getDoubleConfig(String key, double defaultValue) {
        String value = configMapper.selectValueByKey(key);
        if (value == null) return defaultValue;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String getStringConfig(String key, String defaultValue) {
        String value = configMapper.selectValueByKey(key);
        return value != null ? value : defaultValue;
    }

    private void updateConfig(String key, String value) {
        configMapper.updateValueByKey(key, value);
    }
}
```

### 5.4 ConfigMapper.java（Mapper接口）

```java
package com.example.springb.mapper;

import com.example.springb.entity.ModelConfig;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ConfigMapper {

    @Select("SELECT * FROM model_config WHERE page_type = #{pageType} AND is_active = 1 ORDER BY created_at DESC")
    List<ModelConfig> selectByPageType(String pageType);

    @Select("SELECT * FROM model_config WHERE model_path = #{modelPath} LIMIT 1")
    ModelConfig selectByPath(String modelPath);

    @Insert("INSERT INTO model_config (page_type, folder_path, model_name, model_path, model_type, is_active) " +
            "VALUES (#{pageType}, #{folderPath}, #{modelName}, #{modelPath}, #{modelType}, #{isActive})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(ModelConfig model);

    @Delete("DELETE FROM model_config WHERE id = #{id}")
    void deleteById(Long id);

    @Delete("DELETE FROM model_config WHERE page_type = #{pageType} AND folder_path = #{folderPath}")
    void deleteByPageTypeAndFolder(String pageType, String folderPath);

    @Select("SELECT config_value FROM system_config WHERE config_key = #{key}")
    String selectValueByKey(String key);

    @Update("UPDATE system_config SET config_value = #{value} WHERE config_key = #{key}")
    void updateValueByKey(String key, String value);
}
```

---

## 六、前端实现

### 6.1 config.js（前端配置管理工具）

```javascript
// ==================== 配置管理工具模块 ====================
// 与后端 API 交互，管理模型配置、阈值配置、全局配置

import request from './request'

// ==================== 模型配置 API ====================

/**
 * 获取指定页面的模型列表
 * @param {string} pageType - 页面类型：detect/mask/multi
 * @returns {Promise<Array>} 模型列表
 */
export function fetchModels(pageType) {
  return request.get('/api/config/models', { params: { pageType } })
}

/**
 * 添加模型文件夹并扫描
 * @param {string} pageType - 页面类型
 * @param {string} folderPath - 文件夹路径
 * @returns {Promise<Array>} 扫描到的模型列表
 */
export function addModelFolder(pageType, folderPath) {
  return request.post('/api/config/models/folder', { pageType, folderPath })
}

/**
 * 删除模型配置（仅删除数据库记录）
 * @param {number} id - 模型配置ID
 * @returns {Promise}
 */
export function removeModel(id) {
  return request.delete(`/api/config/models/${id}`)
}

/**
 * 重新扫描模型文件夹
 * @param {string} pageType - 页面类型
 * @param {string} folderPath - 文件夹路径
 * @returns {Promise<Array>} 扫描到的模型列表
 */
export function rescanModels(pageType, folderPath) {
  return request.post('/api/config/models/scan', { pageType, folderPath })
}

// ==================== 阈值配置 API ====================

/**
 * 获取所有阈值配置
 * @returns {Promise<Object>} 阈值配置对象 { detect, mask, multi }
 */
export function fetchThresholds() {
  return request.get('/api/config/thresholds')
}

/**
 * 更新阈值配置
 * @param {Object} thresholds - 阈值配置 { detect, mask, multi }
 * @returns {Promise}
 */
export function updateThresholds(thresholds) {
  return request.put('/api/config/thresholds', thresholds)
}

/**
 * 获取指定页面的默认阈值
 * @param {string} pageType - 页面类型
 * @param {number} defaultValue - 默认值
 * @returns {Promise<number>} 阈值
 */
export async function getThreshold(pageType, defaultValue = 0.45) {
  try {
    const res = await fetchThresholds()
    if (res.code === '200' && res.data) {
      return res.data[pageType] ?? defaultValue
    }
  } catch (e) {
    console.warn('获取阈值失败，使用默认值', e)
  }
  return defaultValue
}

// ==================== 全局配置 API ====================

/**
 * 获取全局配置
 * @returns {Promise<Object>} 全局配置 { backendUrl, pythonDetectUrl, pythonSegmentUrl }
 */
export function fetchGlobalConfig() {
  return request.get('/api/config/global')
}

/**
 * 更新全局配置
 * @param {Object} config - 全局配置
 * @returns {Promise}
 */
export function updateGlobalConfig(config) {
  return request.put('/api/config/global', config)
}

// ==================== 辅助函数 ====================

/**
 * 将后端模型列表转换为 el-option 格式
 * @param {Array} models - 后端返回的模型列表
 * @returns {Array} el-option 格式列表 [{ label, value }]
 */
export function formatModelOptions(models) {
  if (!Array.isArray(models)) return []
  return models.map(m => ({
    label: `${m.modelName} (${m.folderPath})`,
    value: m.modelPath,
    ...m
  }))
}
```

### 6.2 Config.vue（配置页面）

```vue
<template>
  <div class="config-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2>系统配置</h2>
      <p class="subtitle">仅管理员可访问：预设默认参数，适配 Jetson 端部署</p>
    </div>

    <!-- 配置标签页 -->
    <section class="config-band">
      <el-tabs v-model="activeTab" type="border-card">
        <!-- 快速诊断配置 -->
        <el-tab-pane label="快速诊断配置" name="detect">
          <ModelConfigPanel 
            page-type="detect" 
            :models="models.detect"
            @refresh="loadModels"
          />
          <ThresholdConfigPanel 
            v-model="thresholds.detect"
            label="默认置信度阈值"
          />
        </el-tab-pane>

        <!-- 精准分割配置 -->
        <el-tab-pane label="精准分割配置" name="mask">
          <ModelConfigPanel 
            page-type="mask" 
            :models="models.mask"
            @refresh="loadModels"
          />
          <ThresholdConfigPanel 
            v-model="thresholds.mask"
            label="默认置信度阈值"
          />
        </el-tab-pane>

        <!-- 批量分割配置 -->
        <el-tab-pane label="批量分割配置" name="multi">
          <ModelConfigPanel 
            page-type="multi" 
            :models="models.multi"
            @refresh="loadModels"
          />
          <ThresholdConfigPanel 
            v-model="thresholds.multi"
            label="默认置信度阈值"
          />
        </el-tab-pane>

        <!-- 全局设置 -->
        <el-tab-pane label="全局设置" name="global">
          <GlobalConfigPanel v-model="globalConfig" />
        </el-tab-pane>
      </el-tabs>
    </section>

    <!-- 操作按钮 -->
    <div class="action-bar">
      <el-button @click="handleReset">重置为默认</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">保存配置</el-button>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { 
  fetchModels, 
  fetchThresholds, 
  updateThresholds,
  fetchGlobalConfig,
  updateGlobalConfig
} from '@/utils/config'
import ModelConfigPanel from './components/ModelConfigPanel.vue'
import ThresholdConfigPanel from './components/ThresholdConfigPanel.vue'
import GlobalConfigPanel from './components/GlobalConfigPanel.vue'

const activeTab = ref('detect')
const saving = ref(false)

// 模型配置
const models = reactive({
  detect: [],
  mask: [],
  multi: []
})

// 阈值配置（范围 0.05-0.95，步长 0.1）
const thresholds = reactive({
  detect: 0.5,
  mask: 0.5,
  multi: 0.3
})

// 全局配置
const globalConfig = reactive({
  backendUrl: 'http://localhost:9527',
  pythonDetectUrl: 'http://localhost:5001',
  pythonSegmentUrl: 'http://localhost:5001'
})

// 加载模型列表
const loadModels = async () => {
  try {
    const pages = ['detect', 'mask', 'multi']
    for (const page of pages) {
      const res = await fetchModels(page)
      if (res.code === '200') {
        models[page] = res.data || []
      }
    }
  } catch (error) {
    ElMessage.error('加载模型列表失败')
    console.error(error)
  }
}

// 加载阈值配置
const loadThresholds = async () => {
  try {
    const res = await fetchThresholds()
    if (res.code === '200' && res.data) {
      thresholds.detect = res.data.detect ?? 0.45
      thresholds.mask = res.data.mask ?? 0.45
      thresholds.multi = res.data.multi ?? 0.25
    }
  } catch (error) {
    ElMessage.error('加载阈值配置失败')
    console.error(error)
  }
}

// 加载全局配置
const loadGlobalConfig = async () => {
  try {
    const res = await fetchGlobalConfig()
    if (res.code === '200' && res.data) {
      Object.assign(globalConfig, res.data)
    }
  } catch (error) {
    ElMessage.error('加载全局配置失败')
    console.error(error)
  }
}

// 保存配置
const handleSave = async () => {
  saving.value = true
  try {
    // 保存阈值
    await updateThresholds({ ...thresholds })
    // 保存全局配置
    await updateGlobalConfig({ ...globalConfig })
    ElMessage.success('配置已保存')
  } catch (error) {
    ElMessage.error('保存配置失败')
    console.error(error)
  } finally {
    saving.value = false
  }
}

// 重置为默认
const handleReset = async () => {
  try {
    await ElMessageBox.confirm('确定要重置所有配置为默认值吗？', '确认重置')
    thresholds.detect = 0.5
    thresholds.mask = 0.5
    thresholds.multi = 0.3
    globalConfig.backendUrl = 'http://localhost:9527'
    globalConfig.pythonDetectUrl = 'http://localhost:5001'
    globalConfig.pythonSegmentUrl = 'http://localhost:5001'
    await handleSave()
    ElMessage.success('已重置为默认配置')
  } catch {
    // 用户取消
  }
}

onMounted(() => {
  loadModels()
  loadThresholds()
  loadGlobalConfig()
})
</script>

<style scoped>
.config-container {
  min-height: calc(100vh - 64px);
  padding: 20px;
  background: var(--app-bg);
}

.page-header,
.config-band {
  margin-bottom: 18px;
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  box-shadow: var(--app-shadow);
}

.page-header {
  padding: 20px 24px;
}

.page-header h2 {
  margin: 0 0 8px;
  font-size: 22px;
  color: var(--app-text);
}

.subtitle {
  margin: 0;
  color: var(--app-text-muted);
}

.config-band {
  padding: 18px;
}

.action-bar {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
```

### 6.3 ModelConfigPanel.vue（模型配置子组件）

```vue
<template>
  <div class="model-config-panel">
    <h3 class="panel-title">模型文件夹配置</h3>
    
    <!-- 添加文件夹 -->
    <div class="folder-input-row">
      <el-input 
        v-model="newFolderPath" 
        placeholder="输入模型文件夹路径，如 D:/models/ 或 /home/jetson/models/"
        clearable
        style="flex: 1"
      />
      <el-button type="primary" :loading="scanning" @click="handleAddFolder">
        <el-icon><Plus /></el-icon>
        添加并扫描
      </el-button>
    </div>

    <!-- 已扫描模型列表 -->
    <div class="models-table-wrapper">
      <el-table :data="models" style="width: 100%" v-loading="loading">
        <el-table-column prop="modelName" label="模型名称" min-width="150" />
        <el-table-column prop="folderPath" label="所属文件夹" min-width="200" show-overflow-tooltip />
        <el-table-column prop="modelType" label="类型" width="80">
          <template #default="{ row }">
            <el-tag size="small">{{ row.modelType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="modelPath" label="完整路径" min-width="250" show-overflow-tooltip />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button 
              type="danger" 
              link 
              size="small"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <el-empty v-if="!models.length" description="暂无模型配置，请添加模型文件夹" />
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { addModelFolder, removeModel } from '@/utils/config'

const props = defineProps({
  pageType: { type: String, required: true },
  models: { type: Array, default: () => [] }
})

const emit = defineEmits(['refresh'])

const newFolderPath = ref('')
const scanning = ref(false)
const loading = ref(false)

// 添加文件夹并扫描
const handleAddFolder = async () => {
  const path = newFolderPath.value.trim()
  if (!path) {
    ElMessage.warning('请输入文件夹路径')
    return
  }
  
  scanning.value = true
  try {
    await addModelFolder(props.pageType, path)
    ElMessage.success('扫描完成，模型已添加到列表')
    newFolderPath.value = ''
    emit('refresh')
  } catch (error) {
    ElMessage.error('扫描失败：' + (error.message || '未知错误'))
  } finally {
    scanning.value = false
  }
}

// 删除模型配置（仅删除数据库记录）
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除模型 "${row.modelName}" 吗？<br><small>注意：此操作仅删除配置记录，不会删除物理文件</small>`,
      '确认删除',
      { dangerouslyUseHTMLString: true, type: 'warning' }
    )
    await removeModel(row.id)
    ElMessage.success('已删除')
    emit('refresh')
  } catch {
    // 用户取消
  }
}
</script>

<style scoped>
.model-config-panel {
  margin-bottom: 24px;
}

.panel-title {
  margin: 0 0 16px;
  font-size: 16px;
  color: var(--app-text);
}

.folder-input-row {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.models-table-wrapper {
  min-height: 200px;
}
</style>
```

### 6.4 ThresholdConfigPanel.vue（阈值配置子组件）

```vue
<template>
  <div class="threshold-config-panel">
    <h3 class="panel-title">{{ label }}</h3>
    <div class="threshold-row">
      <el-slider 
        v-model="localValue" 
        :min="0.05" 
        :max="0.95" 
        :step="0.1"
        style="flex: 1"
        @change="emit('update:modelValue', localValue)"
      />
      <el-input-number 
        v-model="localValue" 
        :min="0.05" 
        :max="0.95" 
        :step="0.1"
        :precision="2"
        style="width: 100px; margin-left: 16px"
        @change="emit('update:modelValue', localValue)"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: Number, default: 0.45 },
  label: { type: String, default: '默认置信度阈值' }
})

const emit = defineEmits(['update:modelValue'])

const localValue = ref(props.modelValue)

watch(() => props.modelValue, (val) => {
  localValue.value = val
})
</script>

<style scoped>
.threshold-config-panel {
  margin-bottom: 24px;
}

.panel-title {
  margin: 0 0 16px;
  font-size: 16px;
  color: var(--app-text);
}

.threshold-row {
  display: flex;
  align-items: center;
}
</style>
```

---

## 七、路由配置

### 7.1 修改 router/index.js

添加 Config 路由，配置 admin 权限：

```javascript
// 在 children 数组中添加
{
  path: 'config',
  meta: { 
    name: '系统配置', 
    requiresAuth: true,
    requiresRole: 'admin'  // 仅管理员可访问
  },
  component: () => import('../views/Config.vue')
}
```

### 7.2 修改 Manager.vue

在"数据管理"子菜单中添加"系统配置"菜单项，仅管理员可见：

```html
<el-sub-menu index="data">
  <template #title>
    <el-icon><TrendCharts /></el-icon>
    <span>数据管理</span>
  </template>
  <el-menu-item index="/manager/dataview">数据可视化</el-menu-item>
  <el-menu-item index="/manager/history">检测历史</el-menu-item>
  <!-- 仅管理员可见 -->
  <el-menu-item v-if="isAdmin" index="/manager/config">系统配置</el-menu-item>
</el-sub-menu>
```

---

## 八、现有页面集成

### 8.1 Detect.vue 集成

**修改点 1：导入配置工具**

```javascript
import { fetchModels, getThreshold, formatModelOptions } from '@/utils/config'
```

**修改点 2：修改 loadModels 函数**

```javascript
const loadModels = async () => {
  modelLoading.value = true
  try {
    // 从后端获取配置的模型列表
    const res = await fetchModels('detect')
    if (res.code === '200') {
      modelOptions.value = formatModelOptions(res.data)
      if (modelOptions.value.length > 0 && !modelPath.value) {
        modelPath.value = modelOptions.value[0].value
      }
    } else {
      ElMessage.warning('加载模型列表失败: ' + res.msg)
    }
  } catch (error) {
    console.error('加载模型列表失败', error)
    ElMessage.error('无法加载模型列表，请检查配置')
  } finally {
    modelLoading.value = false
  }
}
```

**修改点 3：修改模型选择组件**

```vue
<!-- 普通用户只能从下拉列表选择，不允许手动输入 -->
<el-select
  v-model="modelPath"
  class="full-width"
  clearable
  :loading="modelLoading"
  placeholder="选择检测模型"
>
  <el-option
    v-for="item in modelOptions"
    :key="item.value"
    :label="item.label"
    :value="item.value"
  />
</el-select>
```

**修改点 4：添加 applyConfig 函数**

```javascript
// 应用配置
const applyConfig = async () => {
  // 应用默认阈值
  const threshold = await getThreshold('detect', 0.45)
  confThreshold.value = threshold
}
```

**修改点 5：修改 onMounted**

```javascript
onMounted(() => {
  loadRecordFromRoute(route.query.recordId)
  applyConfig()  // 应用配置
  loadModels()
  loadAiConfigStatus()
  // ...
})
```

### 8.2 Mask.vue 集成

与 Detect.vue 完全对称，将 `'detect'` 替换为 `'mask'`。

### 8.3 Multi.vue 集成

```javascript
// 应用配置
const applyConfig = async () => {
  const threshold = await getThreshold('multi', 0.25)
  confidence.value = threshold
}
```

---

## 九、SecurityConfig 配置

在 SecurityConfig.java 中添加配置接口的权限控制：

```java
.requestMatchers("/api/config/models/**").hasRole("admin")
.requestMatchers("/api/config/thresholds").authenticated()
.requestMatchers("/api/config/global").authenticated()
```

---

## 十、测试用例

### 10.1 功能测试

| 测试编号 | 测试项 | 测试步骤 | 预期结果 |
|----------|--------|----------|----------|
| T001 | 权限控制-管理员 | 管理员访问 /manager/config | 页面正常加载 |
| T002 | 权限控制-普通用户 | 普通用户访问 /manager/config | 跳转 403 页面 |
| T003 | 添加模型文件夹 | 输入有效路径，点击添加并扫描 | 扫描完成，模型列表显示 |
| T004 | 删除模型配置 | 点击删除按钮，确认删除 | 仅删除数据库记录，物理文件保留 |
| T005 | 阈值设置范围 | 尝试设置阈值 0.01 或 1.0 | 无法设置，范围限制在 0.05-0.95 |
| T006 | 配置保存 | 修改阈值，点击保存 | 提示保存成功 |
| T007 | Detect 页面模型选择 | 打开 Detect 页面 | 模型下拉列表仅显示已配置模型，不可手动输入 |
| T008 | Detect 页面阈值应用 | 打开 Detect 页面 | 自动应用配置页面设置的阈值 |

### 10.2 兼容性测试

| 测试编号 | 测试项 | 测试环境 | 预期结果 |
|----------|--------|----------|----------|
| TC01 | Windows 路径 | Windows 10/11 | 路径格式正确，扫描正常 |
| TC02 | Linux 路径 | Ubuntu/Jetson | 路径格式正确，扫描正常 |

---

## 十一、部署说明

### 11.1 数据库部署

1. 执行上述 SQL 创建表结构
2. 初始化默认配置数据

### 11.2 后端部署

1. 创建 ConfigController、ConfigService、ConfigMapper、实体类
2. 更新 SecurityConfig 添加权限配置
3. 重新编译部署

### 11.3 前端部署

1. 创建 config.js 工具模块
2. 创建 Config.vue 及子组件
3. 更新 router/index.js 添加路由
4. 更新 Manager.vue 添加菜单项
5. 更新 Detect.vue、Mask.vue、Multi.vue 集成配置
6. 重新构建部署

---

## 十二、更新日志

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-05-20 | 初始版本，使用 localStorage 存储 |
| v1.1 | 2026-05-20 | 重大调整：改用数据库存储，添加权限控制，模型扫描逻辑优化，阈值范围调整为 0.05-0.95 |
