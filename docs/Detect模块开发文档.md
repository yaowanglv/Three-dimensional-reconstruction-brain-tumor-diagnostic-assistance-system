# Detect.vue 脑肿瘤图像辅助诊断分析模块 - 开发文档

> 适用于 Vibe Coding 开发模式

---

## 📋 项目背景

本项目是**基于AI大模型与深度学习的肿瘤辅助诊断系统**，需要在现有SpringBoot+Vue3架构下新增**脑肿瘤图像辅助诊断分析**功能模块。

---

## 🎯 需求概述

### 功能模块
- **页面路径**: `/manager/detect`
- **父组件**: `Manager.vue`
- **页面名称**: 脑肿瘤图像辅助诊断分析

### 核心功能
1. **图像上传**: 用户上传脑肿瘤原始医学图像
2. **智能检测**: 调用Python检测接口，识别脑肿瘤位置
3. **结果展示**: 左右分栏显示原图（左）和检测结果图（右）
4. **AI辅助分析**: 调用大模型API进行智能诊断分析
5. **历史记录**: 所有检测数据持久化存储

---

## 🗄️ 数据库设计

### Detect表结构

```sql
CREATE TABLE `detect` (
    -- 主键
    `id` INT PRIMARY KEY AUTO_INCREMENT COMMENT '检测记录ID',
    
    -- 用户关联
    `user_id` INT NOT NULL COMMENT '操作用户ID',
    `user_name` VARCHAR(50) COMMENT '操作用户名',
    
    -- 原始图像信息
    `original_image_name` VARCHAR(255) COMMENT '原始图像文件名',
    `original_image_url` VARCHAR(500) NOT NULL COMMENT '原始图像存储路径/URL',
    `original_image_size` BIGINT COMMENT '原始图像大小(字节)',
    `original_image_format` VARCHAR(10) COMMENT '原始图像格式(jpg/png/dicom等)',
    
    -- 检测结果图像
    `result_image_url` VARCHAR(500) COMMENT '检测结果图像路径/URL',
    
    -- 检测数据（JSON格式存储检测框、标签等信息）
    `detection_data` JSON COMMENT '检测结果数据: 包含检测框坐标、置信度、肿瘤类型等',
    -- 示例: {"boxes": [[x1,y1,x2,y2], ...], "labels": ["glioma", ...], "scores": [0.95, ...], "tumor_type": "胶质瘤"}
    
    -- AI分析相关
    `ai_model` VARCHAR(50) COMMENT '使用的大模型(deepseek/glm/doubao)',
    `ai_analysis_result` TEXT COMMENT 'AI大模型分析结果文本',
    `ai_analysis_time` DATETIME COMMENT 'AI分析完成时间',
    
    -- 检测状态
    `detect_status` TINYINT DEFAULT 0 COMMENT '检测状态: 0-待检测 1-检测中 2-检测完成 3-检测失败',
    `ai_status` TINYINT DEFAULT 0 COMMENT 'AI分析状态: 0-未分析 1-分析中 2-分析完成 3-分析失败',
    
    -- 时间戳
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间',
    
    -- 备注
    `remark` VARCHAR(500) COMMENT '备注信息'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='脑肿瘤检测记录表';
```

### 字段详细说明

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | INT | 自增主键 |
| `user_id` | INT | 关联admin表，记录谁进行了这次检测 |
| `original_image_url` | VARCHAR | 原始图像在服务器的存储路径 |
| `result_image_url` | VARCHAR | 检测后带框图像的存储路径 |
| `detection_data` | JSON | 核心字段，存储检测算法返回的所有结构化数据 |
| `ai_model` | VARCHAR | 使用的大模型标识 |
| `ai_analysis_result` | TEXT | 大模型返回的分析文本 |
| `detect_status` | TINYINT | 跟踪检测流程状态 |

### detection_data JSON结构示例

```json
{
  "tumor_detected": true,
  "tumor_type": "glioma",
  "tumor_type_cn": "胶质瘤",
  "confidence": 0.94,
  "boxes": [
    {
      "id": 1,
      "x1": 120,
      "y1": 80,
      "x2": 280,
      "y2": 240,
      "width": 160,
      "height": 160,
      "center_x": 200,
      "center_y": 160,
      "label": "tumor",
      "confidence": 0.94,
      "area_pixels": 25600
    }
  ],
  "image_width": 512,
  "image_height": 512,
  "processing_time_ms": 1250
}
```

---

## 🏗️ 后端开发流程

### 1. 创建Entity实体类

**文件**: `springb/src/main/java/com/example/springb/entity/Detect.java`

```java
package com.example.springb.entity;

import java.time.LocalDateTime;

/**
 * 脑肿瘤检测记录实体
 */
public class Detect {
    
    // ============== 数据库字段 ==============
    private Integer id;
    private Integer userId;
    private String userName;
    private String originalImageName;
    private String originalImageUrl;
    private Long originalImageSize;
    private String originalImageFormat;
    private String resultImageUrl;
    private String detectionData;  // JSON字符串
    private String aiModel;
    private String aiAnalysisResult;
    private LocalDateTime aiAnalysisTime;
    private Integer detectStatus;  // 0-待检测 1-检测中 2-完成 3-失败
    private Integer aiStatus;      // 0-未分析 1-分析中 2-完成 3-失败
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String remark;
    
    // ============== 非数据库字段 ==============
    private String ids;
    private String[] idsArr;
    
    // TODO: 生成所有字段的getter和setter
    // 可使用IDEA右键 -> Generate -> Getter and Setter
}
```

### 2. 创建Mapper接口

**文件**: `springb/src/main/java/com/example/springb/mapper/DetectMapper.java`

```java
package com.example.springb.mapper;

import com.example.springb.entity.Detect;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface DetectMapper {
    
    List<Detect> selectAll(Detect detect);
    
    @Insert("INSERT INTO detect (user_id, user_name, original_image_name, " +
            "original_image_url, original_image_size, original_image_format, " +
            "detect_status, create_time) VALUES " +
            "(#{userId}, #{userName}, #{originalImageName}, " +
            "#{originalImageUrl}, #{originalImageSize}, #{originalImageFormat}, " +
            "0, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Detect detect);
    
    void updateById(Detect detect);
    
    @Select("SELECT * FROM detect WHERE id = #{id}")
    Detect selectById(Integer id);
    
    @Delete("DELETE FROM detect WHERE id = #{id}")
    void deleteById(Integer id);
}
```

### 3. 创建Mapper XML

**文件**: `springb/src/main/resources/mapper/DetectMapper.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" 
    "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.springb.mapper.DetectMapper">

    <resultMap id="BaseResultMap" type="com.example.springb.entity.Detect">
        <id column="id" property="id"/>
        <result column="user_id" property="userId"/>
        <result column="user_name" property="userName"/>
        <result column="original_image_name" property="originalImageName"/>
        <result column="original_image_url" property="originalImageUrl"/>
        <result column="original_image_size" property="originalImageSize"/>
        <result column="original_image_format" property="originalImageFormat"/>
        <result column="result_image_url" property="resultImageUrl"/>
        <result column="detection_data" property="detectionData"/>
        <result column="ai_model" property="aiModel"/>
        <result column="ai_analysis_result" property="aiAnalysisResult"/>
        <result column="ai_analysis_time" property="aiAnalysisTime"/>
        <result column="detect_status" property="detectStatus"/>
        <result column="ai_status" property="aiStatus"/>
        <result column="create_time" property="createTime"/>
        <result column="update_time" property="updateTime"/>
        <result column="remark" property="remark"/>
    </resultMap>

    <select id="selectAll" resultMap="BaseResultMap">
        SELECT * FROM detect
        <where>
            <if test="userId != null">
                AND user_id = #{userId}
            </if>
            <if test="detectStatus != null">
                AND detect_status = #{detectStatus}
            </if>
            <if test="idsArr != null and idsArr.length > 0">
                AND id IN
                <foreach collection="idsArr" item="id" open="(" separator="," close=")">
                    #{id}
                </foreach>
            </if>
        </where>
        ORDER BY create_time DESC
    </select>

    <update id="updateById">
        UPDATE detect
        <set>
            <if test="resultImageUrl != null">result_image_url = #{resultImageUrl},</if>
            <if test="detectionData != null">detection_data = #{detectionData},</if>
            <if test="detectStatus != null">detect_status = #{detectStatus},</if>
            <if test="aiModel != null">ai_model = #{aiModel},</if>
            <if test="aiAnalysisResult != null">ai_analysis_result = #{aiAnalysisResult},</if>
            <if test="aiAnalysisTime != null">ai_analysis_time = #{aiAnalysisTime},</if>
            <if test="aiStatus != null">ai_status = #{aiStatus},</if>
            <if test="remark != null">remark = #{remark}</if>
        </set>
        WHERE id = #{id}
    </update>

</mapper>
```

### 4. 创建Service层

**文件**: `springb/src/main/java/com/example/springb/service/DetectService.java`

```java
package com.example.springb.service;

import com.example.springb.entity.Detect;
import com.example.springb.mapper.DetectMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DetectService {
    
    @Resource
    private DetectMapper detectMapper;
    
    public void add(Detect detect) {
        detectMapper.insert(detect);
    }
    
    public void update(Detect detect) {
        detectMapper.updateById(detect);
    }
    
    public void deleteById(Integer id) {
        detectMapper.deleteById(id);
    }
    
    public void deleteBatch(List<Detect> list) {
        for (Detect detect : list) {
            this.deleteById(detect.getId());
        }
    }
    
    public List<Detect> selectAll(Detect detect) {
        return detectMapper.selectAll(detect);
    }
    
    public PageInfo<Detect> selectPage(Integer pageNum, Integer pageSize, Detect detect) {
        PageHelper.startPage(pageNum, pageSize);
        List<Detect> list = detectMapper.selectAll(detect);
        return PageInfo.of(list);
    }
    
    public Detect selectById(Integer id) {
        return detectMapper.selectById(id);
    }
}
```

### 5. 创建Controller层

**文件**: `springb/src/main/java/com/example/springb/controller/DetectController.java`

```java
package com.example.springb.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.example.springb.common.Result;
import com.example.springb.entity.Detect;
import com.example.springb.service.DetectService;
import com.github.pagehelper.PageInfo;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/detect")
public class DetectController {
    
    @Resource
    private DetectService detectService;
    
    // 文件上传路径配置（在application.yml中添加）
    @Value("${file.upload.path:uploads/}")
    private String uploadPath;
    
    @Value("${file.access.url:http://localhost:9527/files/}")
    private String fileAccessUrl;
    
    // Python检测服务地址（在application.yml中配置）
    @Value("${python.detect.url:http://localhost:5000/detect}")
    private String pythonDetectUrl;
    
    // ==================== 基础CRUD接口 ====================
    
    /**
     * 分页查询检测记录
     */
    @GetMapping("/selectPage")
    public Result selectPage(@RequestParam(defaultValue = "1") Integer pageNum,
                             @RequestParam(defaultValue = "10") Integer pageSize,
                             Detect detect) {
        PageInfo<Detect> pageInfo = detectService.selectPage(pageNum, pageSize, detect);
        return Result.success(pageInfo);
    }
    
    /**
     * 根据ID查询
     */
    @GetMapping("/selectById/{id}")
    public Result selectById(@PathVariable Integer id) {
        Detect detect = detectService.selectById(id);
        return Result.success(detect);
    }
    
    /**
     * 删除检测记录
     */
    @DeleteMapping("/delete/{id}")
    public Result delete(@PathVariable Integer id) {
        detectService.deleteById(id);
        return Result.success();
    }
    
    // ==================== 图像上传与检测接口 ====================
    
    /**
     * 上传原始图像并创建检测记录
     */
    @PostMapping("/upload")
    public Result uploadImage(@RequestParam("file") MultipartFile file,
                              @RequestParam("userId") Integer userId,
                              @RequestParam("userName") String userName) {
        try {
            // 1. 生成唯一文件名
            String originalName = file.getOriginalFilename();
            String ext = FileUtil.extName(originalName);
            String newFileName = UUID.randomUUID() + "." + ext;
            
            // 2. 创建日期目录
            String dateDir = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
            String relativePath = "detect/" + dateDir + "/" + newFileName;
            String fullPath = uploadPath + relativePath;
            
            // 3. 确保目录存在
            FileUtil.mkParentDirs(fullPath);
            
            // 4. 保存文件
            File destFile = new File(fullPath);
            file.transferTo(destFile);
            
            // 5. 创建检测记录
            Detect detect = new Detect();
            detect.setUserId(userId);
            detect.setUserName(userName);
            detect.setOriginalImageName(originalName);
            detect.setOriginalImageUrl(relativePath);
            detect.setOriginalImageSize(file.getSize());
            detect.setOriginalImageFormat(ext);
            detect.setDetectStatus(0); // 待检测状态
            
            detectService.add(detect);
            
            // 6. 返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("id", detect.getId());
            result.put("originalImageUrl", fileAccessUrl + relativePath);
            result.put("originalImageName", originalName);
            
            return Result.success(result);
            
        } catch (IOException e) {
            log.error("图像上传失败", e);
            return Result.error("图像上传失败: " + e.getMessage());
        }
    }
    
    /**
     * 调用Python检测服务进行图像检测
     */
    @PostMapping("/startDetect/{id}")
    public Result startDetect(@PathVariable Integer id) {
        try {
            Detect detect = detectService.selectById(id);
            if (detect == null) {
                return Result.error("检测记录不存在");
            }
            
            // 更新为检测中状态
            detect.setDetectStatus(1);
            detectService.update(detect);
            
            // TODO: 调用Python检测服务
            // 这里使用RestTemplate或WebClient调用Python服务
            // 请求体包含: imagePath, recordId
            // 响应包含: detectionData, resultImagePath
            
            // 模拟调用Python服务（实际开发时替换为真实调用）
            // RestTemplate restTemplate = new RestTemplate();
            // PythonDetectRequest request = new PythonDetectRequest(...);
            // PythonDetectResponse response = restTemplate.postForObject(
            //     pythonDetectUrl, request, PythonDetectResponse.class);
            
            // 模拟检测结果（实际开发时删除）
            Map<String, Object> mockDetectionData = new HashMap<>();
            mockDetectionData.put("tumor_detected", true);
            mockDetectionData.put("tumor_type", "glioma");
            mockDetectionData.put("confidence", 0.94);
            mockDetectionData.put("boxes", List.of(
                Map.of("x1", 120, "y1", 80, "x2", 280, "y2", 240, 
                       "label", "tumor", "confidence", 0.94)
            ));
            
            // 更新检测结果
            detect.setDetectStatus(2); // 检测完成
            detect.setDetectionData(JSONUtil.toJsonStr(mockDetectionData));
            detect.setResultImageUrl("detect/202401/result_" + id + ".jpg"); // 实际为Python返回的路径
            detectService.update(detect);
            
            return Result.success(detect);
            
        } catch (Exception e) {
            log.error("检测失败", e);
            // 更新为失败状态
            Detect detect = new Detect();
            detect.setId(id);
            detect.setDetectStatus(3);
            detectService.update(detect);
            return Result.error("检测失败: " + e.getMessage());
        }
    }
    
    /**
     * 调用AI大模型进行辅助分析
     */
    @PostMapping("/aiAnalysis/{id}")
    public Result aiAnalysis(@PathVariable Integer id, 
                            @RequestParam String model) {
        try {
            Detect detect = detectService.selectById(id);
            if (detect == null) {
                return Result.error("检测记录不存在");
            }
            
            if (detect.getDetectStatus() != 2) {
                return Result.error("请先完成图像检测");
            }
            
            // 更新为分析中状态
            detect.setAiStatus(1);
            detect.setAiModel(model);
            detectService.update(detect);
            
            // TODO: 调用大模型API
            // 1. 根据model选择对应的API端点和密钥
            // 2. 构建Prompt，包含检测数据（boxes, labels, tumor_type等）
            // 3. 发送请求获取分析结果
            // 4. 解析并保存结果
            
            // 模拟AI分析（实际开发时替换为真实调用）
            String prompt = buildAnalysisPrompt(detect);
            String aiResult = mockAiAnalysis(prompt, model);
            
            detect.setAiAnalysisResult(aiResult);
            detect.setAiAnalysisTime(LocalDateTime.now());
            detect.setAiStatus(2); // 分析完成
            detectService.update(detect);
            
            return Result.success(detect);
            
        } catch (Exception e) {
            log.error("AI分析失败", e);
            Detect detect = new Detect();
            detect.setId(id);
            detect.setAiStatus(3);
            detectService.update(detect);
            return Result.error("AI分析失败: " + e.getMessage());
        }
    }
    
    /**
     * 构建AI分析Prompt
     */
    private String buildAnalysisPrompt(Detect detect) {
        String detectionData = detect.getDetectionData();
        return String.format(
            "作为一位专业的脑肿瘤影像诊断专家，请根据以下检测结果进行详细分析：\n\n" +
            "【检测数据】\n%s\n\n" +
            "请提供：\n" +
            "1. 肿瘤类型分析\n" +
            "2. 位置与大小评估\n" +
            "3. 严重程度初步判断\n" +
            "4. 建议的后续检查或治疗方案\n" +
            "5. 需要注意的事项",
            detectionData
        );
    }
    
    /**
     * 模拟AI分析（实际开发时删除）
     */
    private String mockAiAnalysis(String prompt, String model) {
        return String.format(
            "【AI辅助分析报告 - %s】\n\n" +
            "根据提供的脑肿瘤影像检测数据，现做出以下分析：\n\n" +
            "1. **肿瘤类型分析**：\n" +
            "   检测结果提示为胶质瘤可能性较高，置信度94%%。胶质瘤是最常见的原发性脑肿瘤，起源于胶质细胞。\n\n" +
            "2. **位置与大小评估**：\n" +
            "   肿瘤位于大脑左叶区域，检测框坐标为(120,80)-(280,240)，" +
            "   约占整个脑影像的10%%左右，属于中等大小病灶。\n\n" +
            "3. **严重程度初步判断**：\n" +
            "   根据肿瘤的位置和大小，建议进一步进行MRI增强扫描和病理活检以确定分级。\n\n" +
            "4. **建议后续方案**：\n" +
            "   - 尽快预约神经外科专家会诊\n" +
            "   - 完善术前检查（血常规、凝血功能等）\n" +
            "   - 考虑手术切除方案\n\n" +
            "5. **注意事项**：\n" +
            "   - 避免剧烈运动和头部外伤\n" +
            "   - 如出现头痛加重、恶心呕吐、视力模糊等症状需立即就医\n" +
            "   - 保持良好心态，积极配合治疗\n\n" +
            "*本分析仅供参考，具体诊断和治疗方案请以专业医生意见为准。",
            model.toUpperCase()
        );
    }
}
```

### 6. 配置文件更新

**文件**: `springb/src/main/resources/application.yml`

```yaml
server:
  port: 9527

spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: root
    password: ${DB_PASSWORD}
    url: jdbc:mysql://localhost:3306/dsecond?useUnicode=true&characterEncoding=utf-8&allowMultiQueries=true&useSSL=false&serverTimezone=GMT%2b8&allowPublicKeyRetrieval=true
  
  # 文件上传配置
  servlet:
    multipart:
      enabled: true
      max-file-size: 50MB
      max-request-size: 100MB

mybatis:
  mapper-locations: classpath:mapper/*.xml
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
    map-underscore-to-camel-case: true

# 自定义配置
file:
  upload:
    path: uploads/  # 文件上传根目录（相对路径或绝对路径）
  access:
    url: http://localhost:9527/files/  # 文件访问URL前缀

# Python检测服务配置
python:
  detect:
    url: http://localhost:5000/detect  # Python检测服务地址

# AI大模型API配置
ai:
  models:
    deepseek:
      api-key: ${DEEPSEEK_API_KEY:your_deepseek_key_here}
      endpoint: https://api.deepseek.com/v1/chat/completions
      model-name: deepseek-chat
    glm:
      api-key: ${GLM_API_KEY:your_glm_key_here}
      endpoint: https://open.bigmodel.cn/api/paas/v4/chat/completions
      model-name: glm-4
    doubao:
      api-key: ${DOUBAO_API_KEY:your_doubao_key_here}
      endpoint: https://ark.cn-beijing.volces.com/api/v3/chat/completions
      model-name: doubao-pro-32k
```

---

## 🎨 前端开发流程

### 1. 添加路由配置

**文件**: `vue/src/router/index.js`

```javascript
import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {path: '/', redirect: '/manager/admin'},
    {path: '/manager', component: () => import('../views/Manager.vue'),
     children: [
       // ... 现有路由
       
       // 新增：脑肿瘤检测页面
       {path: 'detect', meta: {name: '脑肿瘤检测分析'}, component: () => import('../views/Detect.vue')},
     ]
    },
    {path: '/login', component: () => import('../views/Login.vue')},
    {path: '/notFound', name: '404', component: () => import('../views/404.vue')},
  ],
})

export default router
```

### 2. 更新Manager.vue菜单

**文件**: `vue/src/views/Manager.vue`

在`<el-menu>`中添加新菜单项：

```vue
<el-sub-menu index="3">
  <template #title>
    <el-icon><FirstAidKit /></el-icon>
    <span style="font-size: 15px">智能诊断</span>
  </template>
  <el-menu-item index="/manager/detect">脑肿瘤检测分析</el-menu-item>
</el-sub-menu>
```

注意：需要在`<script setup>`中导入图标组件：
```javascript
import { EditPen, FirstAidKit } from '@element-plus/icons-vue'
```

### 3. 创建Detect.vue页面

**文件**: `vue/src/views/Detect.vue`

```vue
<template>
  <div class="detect-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2>脑肿瘤图像辅助诊断分析</h2>
      <p class="subtitle">基于深度学习与大模型的智能诊断系统</p>
    </div>

    <!-- 操作区域 -->
    <div class="operation-area">
      <!-- 图像上传 -->
      <el-upload
        class="upload-component"
        action="http://localhost:9527/detect/upload"
        :data="uploadData"
        :show-file-list="false"
        :on-success="handleUploadSuccess"
        :on-error="handleUploadError"
        :before-upload="beforeUpload"
        accept=".jpg,.jpeg,.png,.dcm"
      >
        <el-button type="primary" :icon="Upload">
          <el-icon><Upload /></el-icon>
          上传医学影像
        </el-button>
      </el-upload>

      <!-- 开始检测按钮 -->
      <el-button 
        type="success" 
        @click="startDetection"
        :loading="detecting"
        :disabled="!currentRecord || currentRecord.detectStatus === 1"
      >
        <el-icon><VideoPlay /></el-icon>
        {{ detecting ? '检测中...' : '开始检测' }}
      </el-button>

      <!-- AI模型选择 -->
      <el-select 
        v-model="selectedModel" 
        placeholder="选择AI大模型"
        style="width: 180px; margin-left: 20px;"
      >
        <el-option label="DeepSeek" value="deepseek" />
        <el-option label="智谱GLM-4" value="glm" />
        <el-option label="字节豆包" value="doubao" />
      </el-select>

      <!-- AI分析按钮 -->
      <el-button 
        type="warning" 
        @click="startAiAnalysis"
        :loading="aiAnalyzing"
        :disabled="!canAiAnalyze"
        style="margin-left: 10px;"
      >
        <el-icon><ChatDotRound /></el-icon>
        {{ aiAnalyzing ? '分析中...' : 'AI辅助分析' }}
      </el-button>
    </div>

    <!-- 图像对比区域 -->
    <div class="image-compare-area" v-if="currentRecord">
      <el-row :gutter="20">
        <!-- 原始图像 -->
        <el-col :span="12">
          <div class="image-card">
            <div class="image-title">原始影像</div>
            <div class="image-wrapper">
              <img 
                v-if="originalImageUrl" 
                :src="originalImageUrl" 
                alt="原始影像"
                class="medical-image"
              />
              <el-empty v-else description="暂无图像" />
            </div>
            <div class="image-info" v-if="currentRecord.originalImageName">
              <span>文件名: {{ currentRecord.originalImageName }}</span>
              <span>大小: {{ formatFileSize(currentRecord.originalImageSize) }}</span>
            </div>
          </div>
        </el-col>

        <!-- 检测结果图像 -->
        <el-col :span="12">
          <div class="image-card">
            <div class="image-title">
              检测结果
              <el-tag 
                :type="detectStatusType" 
                size="small"
                style="margin-left: 10px;"
              >
                {{ detectStatusText }}
              </el-tag>
            </div>
            <div class="image-wrapper">
              <img 
                v-if="resultImageUrl" 
                :src="resultImageUrl" 
                alt="检测结果"
                class="medical-image"
              />
              <el-empty v-else description="等待检测" />
            </div>
            <!-- 检测数据概览 -->
            <div class="detection-summary" v-if="detectionData">
              <el-descriptions :column="2" size="small" border>
                <el-descriptions-item label="肿瘤类型">
                  {{ detectionData.tumor_type_cn || detectionData.tumor_type || '未知' }}
                </el-descriptions-item>
                <el-descriptions-item label="置信度">
                  <el-progress 
                    :percentage="Math.round((detectionData.confidence || 0) * 100)" 
                    :color="confidenceColor"
                  />
                </el-descriptions-item>
                <el-descriptions-item label="检测框数量">
                  {{ detectionData.boxes?.length || 0 }} 个
                </el-descriptions-item>
                <el-descriptions-item label="处理时间">
                  {{ detectionData.processing_time_ms || '-' }} ms
                </el-descriptions-item>
              </el-descriptions>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>

    <!-- AI分析结果 -->
    <div class="ai-analysis-area" v-if="currentRecord?.aiAnalysisResult">
      <el-card>
        <template #header>
          <div class="card-header">
            <span>
              <el-icon><ChatLineRound /></el-icon>
              AI辅助分析报告 
              <el-tag size="small" type="warning">{{ currentRecord.aiModel }}</el-tag>
            </span>
            <span class="analysis-time">
              {{ formatDateTime(currentRecord.aiAnalysisTime) }}
            </span>
          </div>
        </template>
        <div class="analysis-content">
          <pre>{{ currentRecord.aiAnalysisResult }}</pre>
        </div>
      </el-card>
    </div>

    <!-- 检测历史记录 -->
    <div class="history-area">
      <el-divider content-position="left">检测历史</el-divider>
      <el-table :data="historyList" style="width: 100%" v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="originalImageName" label="文件名" show-overflow-tooltip />
        <el-table-column prop="detectStatus" label="检测状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.detectStatus)">
              {{ getStatusText(row.detectStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="aiStatus" label="AI分析" width="100">
          <template #default="{ row }">
            <el-tag :type="getAiStatusType(row.aiStatus)">
              {{ getAiStatusText(row.aiStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="loadRecord(row)">查看</el-button>
            <el-button type="danger" size="small" @click="deleteRecord(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[5, 10, 20]"
          layout="total, sizes, prev, pager, next"
          :total="total"
          @size-change="loadHistory"
          @current-change="loadHistory"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, VideoPlay, ChatDotRound, ChatLineRound } from '@element-plus/icons-vue'
import request from '@/utils/request.js'

// ==================== 响应式数据 ====================

const currentRecord = ref(null)
const historyList = ref([])
const loading = ref(false)
const detecting = ref(false)
const aiAnalyzing = ref(false)
const selectedModel = ref('deepseek')

// 分页
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 上传数据
const uploadData = computed(() => {
  const userStr = localStorage.getItem('code_user')
  const user = userStr ? JSON.parse(userStr) : {}
  return {
    userId: user.id || 1,
    userName: user.name || '管理员'
  }
})

// 图像URL
const originalImageUrl = computed(() => {
  if (!currentRecord.value?.originalImageUrl) return ''
  return `http://localhost:9527/files/${currentRecord.value.originalImageUrl}`
})

const resultImageUrl = computed(() => {
  if (!currentRecord.value?.resultImageUrl) return ''
  return `http://localhost:9527/files/${currentRecord.value.resultImageUrl}`
})

// 解析检测数据
const detectionData = computed(() => {
  if (!currentRecord.value?.detectionData) return null
  try {
    return JSON.parse(currentRecord.value.detectionData)
  } catch {
    return null
  }
})

// 检测状态
const detectStatusType = computed(() => {
  const status = currentRecord.value?.detectStatus
  const types = { 0: 'info', 1: 'warning', 2: 'success', 3: 'danger' }
  return types[status] || 'info'
})

const detectStatusText = computed(() => {
  const status = currentRecord.value?.detectStatus
  const texts = { 0: '待检测', 1: '检测中', 2: '检测完成', 3: '检测失败' }
  return texts[status] || '未知'
})

// 是否可进行AI分析
const canAiAnalyze = computed(() => {
  return currentRecord.value?.detectStatus === 2 && 
         currentRecord.value?.aiStatus !== 1
})

// 置信度颜色
const confidenceColor = computed(() => {
  const conf = detectionData.value?.confidence || 0
  if (conf >= 0.9) return '#67C23A'
  if (conf >= 0.7) return '#E6A23C'
  return '#F56C6C'
})

// ==================== 方法 ====================

// 上传前校验
const beforeUpload = (file) => {
  const validTypes = ['image/jpeg', 'image/jpg', 'image/png', 'application/dicom']
  const isValidType = validTypes.some(type => file.type.includes(type.replace('image/', '')))
  
  if (!isValidType && !file.name.match(/\.(jpg|jpeg|png|dcm)$/i)) {
    ElMessage.error('请上传 JPG、PNG 或 DICOM 格式的医学影像')
    return false
  }
  
  const maxSize = 50 * 1024 * 1024 // 50MB
  if (file.size > maxSize) {
    ElMessage.error('文件大小不能超过 50MB')
    return false
  }
  
  return true
}

// 上传成功
const handleUploadSuccess = (res) => {
  if (res.code === '200') {
    ElMessage.success('影像上传成功')
    loadRecordById(res.data.id)
    loadHistory()
  } else {
    ElMessage.error(res.msg || '上传失败')
  }
}

// 上传失败
const handleUploadError = () => {
  ElMessage.error('上传失败，请检查网络连接')
}

// 开始检测
const startDetection = async () => {
  if (!currentRecord.value?.id) {
    ElMessage.warning('请先上传影像')
    return
  }
  
  detecting.value = true
  try {
    const res = await request.post(`/detect/startDetect/${currentRecord.value.id}`)
    if (res.code === '200') {
      ElMessage.success('检测完成')
      currentRecord.value = res.data
    } else {
      ElMessage.error(res.msg || '检测失败')
    }
  } catch (error) {
    ElMessage.error('检测请求失败')
  } finally {
    detecting.value = false
  }
}

// AI辅助分析
const startAiAnalysis = async () => {
  if (!selectedModel.value) {
    ElMessage.warning('请选择AI模型')
    return
  }
  
  aiAnalyzing.value = true
  try {
    const res = await request.post(
      `/detect/aiAnalysis/${currentRecord.value.id}?model=${selectedModel.value}`
    )
    if (res.code === '200') {
      ElMessage.success('AI分析完成')
      currentRecord.value = res.data
    } else {
      ElMessage.error(res.msg || '分析失败')
    }
  } catch (error) {
    ElMessage.error('AI分析请求失败')
  } finally {
    aiAnalyzing.value = false
  }
}

// 加载记录详情
const loadRecordById = async (id) => {
  try {
    const res = await request.get(`/detect/selectById/${id}`)
    if (res.code === '200') {
      currentRecord.value = res.data
    }
  } catch (error) {
    console.error('加载记录失败', error)
  }
}

// 从历史列表加载记录
const loadRecord = (row) => {
  currentRecord.value = row
}

// 删除记录
const deleteRecord = async (id) => {
  try {
    await ElMessageBox.confirm('确定删除这条检测记录吗？', '提示', {
      type: 'warning'
    })
    const res = await request.delete(`/detect/delete/${id}`)
    if (res.code === '200') {
      ElMessage.success('删除成功')
      if (currentRecord.value?.id === id) {
        currentRecord.value = null
      }
      loadHistory()
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// 加载历史记录
const loadHistory = async () => {
  loading.value = true
  try {
    const res = await request.get('/detect/selectPage', {
      params: {
        pageNum: pageNum.value,
        pageSize: pageSize.value
      }
    })
    if (res.code === '200') {
      historyList.value = res.data.list
      total.value = res.data.total
    }
  } catch (error) {
    ElMessage.error('加载历史记录失败')
  } finally {
    loading.value = false
  }
}

// ==================== 工具函数 ====================

const formatFileSize = (bytes) => {
  if (!bytes) return '-'
  const units = ['B', 'KB', 'MB', 'GB']
  let size = bytes
  let unitIndex = 0
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024
    unitIndex++
  }
  return `${size.toFixed(2)} ${units[unitIndex]}`
}

const formatDateTime = (datetime) => {
  if (!datetime) return '-'
  return new Date(datetime).toLocaleString('zh-CN')
}

const getStatusType = (status) => {
  const types = { 0: 'info', 1: 'warning', 2: 'success', 3: 'danger' }
  return types[status] || 'info'
}

const getStatusText = (status) => {
  const texts = { 0: '待检测', 1: '检测中', 2: '完成', 3: '失败' }
  return texts[status] || '未知'
}

const getAiStatusType = (status) => {
  const types = { 0: 'info', 1: 'warning', 2: 'success', 3: 'danger' }
  return types[status] || 'info'
}

const getAiStatusText = (status) => {
  const texts = { 0: '未分析', 1: '分析中', 2: '完成', 3: '失败' }
  return texts[status] || '未知'
}

// ==================== 生命周期 ====================

onMounted(() => {
  loadHistory()
})
</script>

<style scoped>
.detect-container {
  padding: 20px;
}

.page-header {
  text-align: center;
  margin-bottom: 30px;
}

.page-header h2 {
  margin: 0 0 10px 0;
  color: #303133;
}

.subtitle {
  color: #909399;
  margin: 0;
}

.operation-area {
  display: flex;
  align-items: center;
  margin-bottom: 30px;
  padding: 20px;
  background: #f5f7fa;
  border-radius: 8px;
}

.image-compare-area {
  margin-bottom: 30px;
}

.image-card {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.1);
  overflow: hidden;
}

.image-title {
  padding: 15px;
  background: #f5f7fa;
  font-weight: bold;
  border-bottom: 1px solid #ebeef5;
}

.image-wrapper {
  height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fafafa;
  padding: 20px;
}

.medical-image {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.image-info {
  padding: 10px 15px;
  font-size: 12px;
  color: #606266;
  background: #f5f7fa;
}

.image-info span {
  margin-right: 20px;
}

.detection-summary {
  padding: 15px;
  border-top: 1px solid #ebeef5;
}

.ai-analysis-area {
  margin-bottom: 30px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.analysis-time {
  color: #909399;
  font-size: 12px;
}

.analysis-content {
  max-height: 400px;
  overflow-y: auto;
}

.analysis-content pre {
  margin: 0;
  white-space: pre-wrap;
  word-wrap: break-word;
  font-family: inherit;
  line-height: 1.8;
  color: #303133;
}

.history-area {
  margin-top: 30px;
}

.pagination-wrapper {
  margin-top: 20px;
  text-align: right;
}

:deep(.el-button .el-icon) {
  margin-right: 5px;
}
</style>
```

---

## 🔌 Python检测服务接口规范

### 接口地址
```
POST http://localhost:5000/detect
```

### 请求参数

```json
{
  "recordId": 123,
  "imagePath": "/path/to/original/image.jpg",
  "imageUrl": "http://localhost:9527/files/detect/202401/xxx.jpg"
}
```

### 响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "recordId": 123,
    "tumorDetected": true,
    "tumorType": "glioma",
    "tumorTypeCn": "胶质瘤",
    "confidence": 0.94,
    "boxes": [
      {
        "id": 1,
        "x1": 120,
        "y1": 80,
        "x2": 280,
        "y2": 240,
        "label": "tumor",
        "confidence": 0.94,
        "areaPixels": 25600
      }
    ],
    "imageWidth": 512,
    "imageHeight": 512,
    "processingTimeMs": 1250,
    "resultImagePath": "/path/to/result/image.jpg",
    "resultImageUrl": "http://localhost:9527/files/detect/202401/result_xxx.jpg"
  }
}
```

---

## 🤖 AI大模型API调用规范

### DeepSeek

```java
// 请求示例
POST https://api.deepseek.com/v1/chat/completions
Headers:
  Authorization: Bearer {api_key}
  Content-Type: application/json

Body:
{
  "model": "deepseek-chat",
  "messages": [
    {"role": "system", "content": "你是一位专业的脑肿瘤影像诊断专家..."},
    {"role": "user", "content": prompt}
  ],
  "temperature": 0.7
}
```

### 智谱GLM-4

```java
// 请求示例
POST https://open.bigmodel.cn/api/paas/v4/chat/completions
Headers:
  Authorization: Bearer {api_key}
  Content-Type: application/json

Body:
{
  "model": "glm-4",
  "messages": [
    {"role": "system", "content": "你是一位专业的脑肿瘤影像诊断专家..."},
    {"role": "user", "content": prompt}
  ]
}
```

### 字节豆包

```java
// 请求示例
POST https://ark.cn-beijing.volces.com/api/v3/chat/completions
Headers:
  Authorization: Bearer {api_key}
  Content-Type: application/json

Body:
{
  "model": "doubao-pro-32k",
  "messages": [
    {"role": "system", "content": "你是一位专业的脑肿瘤影像诊断专家..."},
    {"role": "user", "content": prompt}
  ]
}
```

---

## 🧪 测试检查清单

### 后端测试
- [ ] 数据库表创建成功
- [ ] 图像上传接口正常
- [ ] 检测接口正常（可先用Mock数据）
- [ ] AI分析接口正常（可先用Mock数据）
- [ ] CRUD接口正常

### 前端测试
- [ ] 路由访问正常
- [ ] 菜单显示正常
- [ ] 图像上传正常
- [ ] 检测按钮功能正常
- [ ] AI分析按钮功能正常
- [ ] 左右图像对比显示正常
- [ ] 历史记录列表正常

### 集成测试
- [ ] 端到端流程测试
- [ ] Python服务调用测试
- [ ] AI大模型调用测试

---

## 📦 依赖清单

### 后端新增依赖（pom.xml）

```xml
<!-- 已在项目中，无需新增 -->
<!-- - Hutool (文件操作、JSON处理) -->
<!-- - MyBatis -->
<!-- - PageHelper -->

<!-- 如需调用外部API，可添加 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

### 前端

```bash
# 确保已安装 Element Plus 图标
npm install @element-plus/icons-vue
```

---

## 🔐 安全注意事项

1. **API密钥管理**: 将DeepSeek/GLM/豆包的API密钥配置在环境变量或配置中心，不要硬编码
2. **文件上传限制**: 限制上传文件类型和大小，防止恶意文件上传
3. **路径安全**: 文件存储路径做好校验，防止目录遍历攻击
4. **数据隐私**: 医学影像数据属于敏感信息，做好访问控制和日志审计

---

## 📝 开发顺序建议

1. ✅ 创建数据库表
2. ✅ 创建后端Entity、Mapper、Service、Controller（先用Mock数据）
3. ✅ 配置application.yml
4. ✅ 更新前端路由和菜单
5. ✅ 创建Detect.vue页面
6. ⬜ 实现Python检测服务接口对接
7. ⬜ 实现AI大模型API对接
8. ⬜ 端到端联调测试

---

*文档版本: 1.0*
*适用项目: 基于AI大模型与深度学习的肿瘤辅助诊断系统*
