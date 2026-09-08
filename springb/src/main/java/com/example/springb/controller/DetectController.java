package com.example.springb.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.example.springb.common.Result;
import com.example.springb.entity.Detect;
import com.example.springb.entity.DetectionLog;
import com.example.springb.service.AIService;
import com.example.springb.service.ConfigService;
import com.example.springb.service.DetectService;
import com.example.springb.service.DetectionLogService;
import com.github.pagehelper.PageInfo;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/detect")
public class DetectController {

    private static final Logger log = LoggerFactory.getLogger(DetectController.class);

    @Resource
    private DetectService detectService;
    
    @Resource
    private DetectionLogService detectionLogService;

    @Resource
    private AIService aiService;

    @Resource
    private ConfigService configService;

    // 文件上传路径配置
    @Value("${file.upload.path:uploads/}")
    private String uploadPath;

    @Value("${file.access.url:http://localhost:9527/files/}")
    private String fileAccessUrl;

    // Python检测服务地址
    @Value("${python.detect.url:http://localhost:5000}")
    private String pythonDetectUrl;

    @Value("${python.segment.url:http://localhost:3408}")
    private String pythonSegmentUrl;

    // ==================== 基础CRUD接口 ====================

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public Result health() {
        return Result.success("Detect服务运行正常");
    }

    /**
     * 检查Python检测服务状态
     */
    @GetMapping("/pythonHealth")
    public Result pythonHealth() {
        try {
            String url = getPythonDetectUrl() + "/health";
            HttpResponse response = HttpRequest.get(url)
                    .timeout(5000)
                    .execute();
            
            if (response.getStatus() == 200) {
                return Result.success(JSONUtil.parseObj(response.body()));
            } else {
                return Result.error("Python检测服务响应异常: " + response.getStatus());
            }
        } catch (Exception e) {
            log.error("Python检测服务连接失败", e);
            return Result.error("Python检测服务连接失败: " + e.getMessage());
        }
    }

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
        log.info("收到上传请求: userId={}, userName={}, fileName={}, fileSize={}", 
                userId, userName, file.getOriginalFilename(), file.getSize());
        
        try {
            // 检查配置
            log.debug("uploadPath={}, fileAccessUrl={}", uploadPath, fileAccessUrl);
            
            // 1. 生成唯一文件名
            String originalName = file.getOriginalFilename();
            String ext = FileUtil.extName(originalName);
            String newFileName = UUID.randomUUID().toString() + "." + ext;

            // 2. 创建日期目录
            String dateDir = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
            String relativePath = "detect/" + dateDir + "/" + newFileName;
            
            // 3. 构建完整路径
            String basePath = System.getProperty("user.dir");
            // 统一使用 / 作为分隔符存储，File 类会自动处理
            String fullDir = basePath + "/" + uploadPath + "detect/" + dateDir;
            String fullPath = basePath + "/" + uploadPath + relativePath;
            
            log.info("文件保存目录: {}", fullDir);
            log.info("文件保存路径: {}", fullPath);

            // 4. 确保目录存在
            File dir = new File(fullDir);
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                log.info("创建目录: {}, 结果: {}", fullDir, created);
                if (!created) {
                    return Result.error("创建上传目录失败: " + fullDir);
                }
            }

            // 5. 保存文件
            File destFile = new File(fullPath);
            file.transferTo(destFile);
            log.info("文件保存成功: {}", fullPath);

            // 6. 创建检测记录
            Detect detect = new Detect();
            detect.setUserId(userId);
            detect.setUserName(userName);
            detect.setOriginalImageName(originalName);
            detect.setOriginalImageUrl(relativePath);
            detect.setOriginalImageSize(file.getSize());
            detect.setOriginalImageFormat(ext);
            detect.setDetectStatus(0); // 待检测状态
            detect.setAiStatus(0);     // 未分析状态

            detectService.add(detect);
            log.info("检测记录创建成功: id={}", detect.getId());

            // 7. 返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("id", detect.getId());
            result.put("originalImageUrl", fileAccessUrl + relativePath);
            result.put("originalImageName", originalName);

            return Result.success(result);

        } catch (IOException e) {
            log.error("图像上传失败", e);
            return Result.error("图像上传失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("图像上传异常", e);
            return Result.error("图像上传异常: " + e.getMessage());
        }
    }

    /**
     * 调用Python检测服务进行图像检测
     * 
     * 参数:
     * - id: 检测记录ID
     * - ptPath: 模型文件路径 (可选，默认使用配置路径)
     * - conf: 置信度阈值 (可选，默认0.25)
     */
    @PostMapping("/startDetect/{id}")
    public Result startDetect(@PathVariable Integer id,
                              @RequestParam(required = false) String ptPath,
                              @RequestParam(required = false) Double conf,
                              @RequestParam(required = false, defaultValue = "detect") String mode,
                              @RequestParam(required = false) Integer inputSize,
                              @RequestParam(required = false) Integer nChannels,
                              @RequestParam(required = false) String modelFormat,
                              @RequestParam(required = false) String segmentService) {
        // 设置默认值
        if (ptPath == null || ptPath.isEmpty()) {
            ptPath = "runs/Third/(rtdetr-CSP-PMSFA)+(rtdetr-HyperCompute-MFM)/weights/best.pt";
        }
        if (conf == null) {
            conf = configService.getThreshold(
                    "segment".equalsIgnoreCase(mode) || "mask".equalsIgnoreCase(mode) ? "mask" : "detect",
                    0.25
            );
        }
        boolean segmentMode = "segment".equalsIgnoreCase(mode) || "mask".equalsIgnoreCase(mode);
        String normalizedSegmentService = normalizeSegmentService(segmentService);
        try {
            Detect detect = detectService.selectById(id);
            if (detect == null) {
                return Result.error("检测记录不存在");
            }

            // 保存检测模式
            detect.setDetectMode(segmentMode ? "segment" : "detect");

            // 更新为检测中状态
            detect.setDetectStatus(1);
            detectService.update(detect);

            if (inputSize == null) {
                inputSize = segmentMode ? 512 : null;
            }
            if (nChannels == null) {
                nChannels = 3;
            }
            if (modelFormat == null || modelFormat.isBlank()) {
                modelFormat = inferModelFormat(ptPath);
            }

            log.info("开始检测记录: {}, ptPath={}, conf={}, mode={}, inputSize={}, nChannels={}, modelFormat={}",
                    id, ptPath, conf, mode, inputSize, nChannels, modelFormat);

            // 构建图像完整路径
            String basePath = System.getProperty("user.dir");
            String imagePath = basePath + "/" + uploadPath + detect.getOriginalImageUrl();
            
            File imageFile = new File(imagePath);
            if (!imageFile.exists()) {
                throw new RuntimeException("图像文件不存在: " + imagePath);
            }

            // 调用Python检测/UNet分割服务
            String url = getPythonServiceUrl(segmentMode, normalizedSegmentService);
            log.info("调用Python检测服务: {}", url);
            
            HttpRequest pythonRequest = HttpRequest.post(url)
                    .form("file", imageFile)
                    .form("pt_path", ptPath)
                    .form("conf", String.valueOf(conf))
                    .form("mode", segmentMode ? "segment" : "detect")
                    .form("return_mask_images", segmentMode ? "true" : "false")
                    .form("save_result", "false")
                    .form("return_image", "true")
                    .timeout(120000);

            if (segmentMode && "unet".equals(normalizedSegmentService)) {
                pythonRequest
                        .form("input_size", String.valueOf(inputSize))
                        .form("n_channels", String.valueOf(nChannels))
                        .form("model_format", modelFormat);
            }

            HttpResponse response = pythonRequest.execute();

            if (response.getStatus() != 200) {
                throw new RuntimeException("Python服务响应异常: " + response.getStatus());
            }

            // 解析响应
            String responseBody = response.body();
            log.debug("Python服务响应: {}", responseBody);
            
            cn.hutool.json.JSONObject jsonResponse = JSONUtil.parseObj(responseBody);
            int code = jsonResponse.getInt("code", -1);
            
            if (code != 0) {
                String message = jsonResponse.getStr("message", "检测失败");
                throw new RuntimeException(message);
            }

            // 解析检测结果
            cn.hutool.json.JSONObject data = jsonResponse.getJSONObject("data");
            cn.hutool.json.JSONObject detectionData = data.getJSONObject("detection");
            if (segmentMode) {
                detectionData.set("segment_service", normalizedSegmentService);
            }
            
            // 获取base64结果图并保存到Spring Boot uploads目录
            String dateDir = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
            String resultImageBase64 = data.getStr("result_image_base64");
            String resultImagePath = saveBase64Image(basePath, dateDir, segmentMode ? "segment" : "result", id, resultImageBase64, "jpg");

            if (resultImagePath != null) {
                detectionData.set("result_image_url", resultImagePath);
            } else {
                log.warn("Python服务未返回结果图像base64");
            }

            if (segmentMode) {
                String maskImagePath = saveBase64Image(basePath, dateDir, "mask", id, data.getStr("mask_image_base64"), "png");
                String overlayImagePath = saveBase64Image(basePath, dateDir, "overlay", id, data.getStr("overlay_image_base64"), "jpg");
                if (maskImagePath != null) {
                    detectionData.set("mask_image_url", maskImagePath);
                }
                if (overlayImagePath != null) {
                    detectionData.set("overlay_image_url", overlayImagePath);
                }
            }

            // 更新检测记录
            detect.setDetectStatus(2); // 检测完成
            detect.setDetectionData(detectionData.toString());
            if (resultImagePath != null) {
                detect.setResultImageUrl(resultImagePath);
            }
            detectService.update(detect);
            
            // 记录检测日志
            try {
                DetectionLog logEntry = new DetectionLog();
                logEntry.setDetectId(detect.getId());
                logEntry.setUserId(detect.getUserId());
                logEntry.setUserName(detect.getUserName());
                logEntry.setModelName(ptPath);
                logEntry.setConfThreshold(conf);
                logEntry.setAiModel(detect.getAiModel());
                logEntry.setCreateTime(LocalDateTime.now());
                
                // 解析肿瘤类型和置信度（分割优先从polygons取最高置信度，检测从boxes取）
                if (detectionData.containsKey("tumor_type")) {
                    logEntry.setTumorType(detectionData.getStr("tumor_type"));
                }
                if (detectionData.containsKey("polygons") && !detectionData.getJSONArray("polygons").isEmpty()) {
                    cn.hutool.json.JSONArray polygons = detectionData.getJSONArray("polygons");
                    double maxConf = 0;
                    for (int i = 0; i < polygons.size(); i++) {
                        cn.hutool.json.JSONObject polygon = polygons.getJSONObject(i);
                        if (polygon.containsKey("confidence")) {
                            maxConf = Math.max(maxConf, polygon.getDouble("confidence"));
                        }
                    }
                    if (maxConf > 0) {
                        logEntry.setConfidence(maxConf);
                    }
                } else if (detectionData.containsKey("boxes") && !detectionData.getJSONArray("boxes").isEmpty()) {
                    cn.hutool.json.JSONArray boxes = detectionData.getJSONArray("boxes");
                    double maxConf = 0;
                    for (int i = 0; i < boxes.size(); i++) {
                        cn.hutool.json.JSONObject box = boxes.getJSONObject(i);
                        if (box.containsKey("confidence")) {
                            maxConf = Math.max(maxConf, box.getDouble("confidence"));
                        }
                    }
                    if (maxConf > 0) {
                        logEntry.setConfidence(maxConf);
                    }
                } else if (detectionData.containsKey("confidence")) {
                    logEntry.setConfidence(detectionData.getDouble("confidence"));
                }
                
                detectionLogService.addLog(logEntry);
                log.info("检测日志记录成功: detectId={}", detect.getId());
            } catch (Exception ex) {
                log.error("记录检测日志失败", ex);
            }

            return Result.success(detect);

        } catch (Exception e) {
            log.error("检测失败", e);
            Detect detect = new Detect();
            detect.setId(id);
            detect.setDetectStatus(3); // 检测失败
            detectService.update(detect);
            return Result.error("检测失败: " + e.getMessage());
        }
    }

    private String saveBase64Image(String basePath, String dateDir, String prefix, Integer id, String base64Image, String extension) {
        if (base64Image == null || base64Image.isEmpty()) {
            return null;
        }

        String resultFileName = prefix + "_" + id + "_" + System.currentTimeMillis() + "." + extension;
        String resultRelativePath = "detect/" + dateDir + "/" + resultFileName;
        String resultFullPath = basePath + "/" + uploadPath + resultRelativePath;

        File resultDir = new File(basePath + "/" + uploadPath + "detect/" + dateDir);
        if (!resultDir.exists()) {
            resultDir.mkdirs();
        }

        byte[] imageBytes = Base64.getDecoder().decode(base64Image);
        FileUtil.writeBytes(imageBytes, resultFullPath);
        log.info("结果图像已保存: {}, 大小: {} bytes", resultFullPath, imageBytes.length);
        return resultRelativePath;
    }

    /**
     * 直接检测接口（上传图像并立即检测）
     * 
     * 参数:
     * - file: 图像文件 (required)
     * - ptPath: 模型文件路径 (required)
     * - conf: 置信度阈值 (optional, default=0.25)
     * - userId: 用户ID (optional)
     * - userName: 用户名 (optional)
     */
    @PostMapping("/detectDirect")
    public Result detectDirect(@RequestParam("file") MultipartFile file,
                               @RequestParam("ptPath") String ptPath,
                               @RequestParam(required = false, defaultValue = "0.25") Double conf,
                               @RequestParam(required = false) Integer userId,
                               @RequestParam(required = false) String userName) {
        log.info("收到直接检测请求: ptPath={}, conf={}, fileName={}", ptPath, conf, file.getOriginalFilename());
        
        File tempFile = null;
        try {
            // 1. 调用Python检测服务
            String url = getPythonDetectUrl() + "/detect";
            log.info("调用Python服务URL: {}", url);
            
            // 创建临时文件
            tempFile = File.createTempFile("detect_", "_" + file.getOriginalFilename());
            file.transferTo(tempFile);
            log.info("临时文件创建成功: {}, 大小: {} bytes", tempFile.getAbsolutePath(), tempFile.length());
            
            // 构建multipart请求（不要手动设置Content-Type，Hutool会自动处理）
            // 使用HashMap来指定文件名
            java.util.HashMap<String, Object> paramMap = new java.util.HashMap<>();
            paramMap.put("file", tempFile);
            paramMap.put("pt_path", ptPath);
            paramMap.put("conf", String.valueOf(conf));
            paramMap.put("save_result", "false");
            paramMap.put("return_image", "true");
            
            HttpResponse response = HttpRequest.post(url)
                    .form(paramMap)
                    .timeout(120000)
                    .execute();
            
            log.info("Python服务响应状态: {}", response.getStatus());
            log.debug("Python服务响应体: {}", response.body());

            if (response.getStatus() != 200) {
                log.error("Python服务响应异常: {}, 响应体: {}", response.getStatus(), response.body());
                return Result.error("Python服务响应异常: " + response.getStatus());
            }

            // 2. 解析响应
            String responseBody = response.body();
            log.debug("Python服务响应体: {}", responseBody);
            cn.hutool.json.JSONObject jsonResponse = JSONUtil.parseObj(responseBody);
            int code = jsonResponse.getInt("code", -1);
            
            if (code != 0) {
                String message = jsonResponse.getStr("message", "检测失败");
                log.error("Python服务返回错误: {}", message);
                return Result.error(message);
            }

            // 3. 保存结果图像（如果需要记录到数据库）
            cn.hutool.json.JSONObject data = jsonResponse.getJSONObject("data");
            String resultImageBase64 = data.getStr("result_image_base64");
            String resultImageUrl = null;
            
            if (userId != null && resultImageBase64 != null && !resultImageBase64.isEmpty()) {
                // 保存到数据库
                String basePath = System.getProperty("user.dir");
                String dateDir = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
                String resultFileName = "result_" + System.currentTimeMillis() + ".jpg";
                String resultRelativePath = "detect/" + dateDir + "/" + resultFileName;
                String resultFullPath = basePath + "/" + uploadPath + resultRelativePath;
                
                File resultDir = new File(basePath + "/" + uploadPath + "detect/" + dateDir);
                if (!resultDir.exists()) {
                    resultDir.mkdirs();
                }
                
                byte[] imageBytes = Base64.getDecoder().decode(resultImageBase64);
                FileUtil.writeBytes(imageBytes, resultFullPath);
                resultImageUrl = fileAccessUrl + resultRelativePath;
                
                // 创建检测记录
                Detect detect = new Detect();
                detect.setUserId(userId);
                detect.setUserName(userName != null ? userName : "匿名用户");
                detect.setOriginalImageName(file.getOriginalFilename());
                detect.setOriginalImageSize(file.getSize());
                detect.setOriginalImageFormat(FileUtil.extName(file.getOriginalFilename()));
                detect.setDetectStatus(2);
                detect.setDetectionData(data.getJSONObject("detection").toString());
                detect.setResultImageUrl(resultRelativePath);
                detect.setAiStatus(0);
                
                detectService.add(detect);
                
                // 添加记录ID到返回数据
                data.set("recordId", detect.getId());
            }

            // 4. 返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("detection", data.getJSONObject("detection"));
            result.put("resultImageBase64", resultImageBase64);
            result.put("resultImageUrl", resultImageUrl);
            result.put("confThreshold", data.get("conf_threshold"));
            result.put("modelPath", data.get("model_path"));
            result.put("recordId", data.get("recordId"));

            return Result.success(result);

        } catch (Exception e) {
            log.error("直接检测失败", e);
            return Result.error("检测失败: " + e.getMessage());
        } finally {
            // 清理临时文件
            if (tempFile != null && tempFile.exists()) {
                boolean deleted = tempFile.delete();
                log.debug("临时文件删除{}: {}", deleted ? "成功" : "失败", tempFile.getAbsolutePath());
            }
        }
    }

    /**
     * 列出可用的YOLO脑肿瘤检测/分割模型
     * @param dir 子目录名，如 detect、mask，为空则扫描 brain 下所有子目录
     * 返回格式: [{"label":"模型名.pt","value":"D:\\...\\best.pt"},...]
     */
    @GetMapping("/listModels")
    public Result listModels(@RequestParam(required = false) String dir) {
        try {
            String brainDir = "D:/algorithms/V11-dmt/runs/brain";
            java.util.List<java.util.Map<String, String>> models = new java.util.ArrayList<>();

            if (dir != null && !dir.isEmpty()) {
                // 只扫描指定子目录
                java.io.File subDir = new java.io.File(brainDir, dir);
                if (!subDir.exists() || !subDir.isDirectory()) {
                    return Result.error("模型目录不存在: " + subDir.getAbsolutePath());
                }
                addModelsFromDir(subDir, models);
            } else {
                // 扫描 brain 下所有子目录（兼容旧调用）
                java.io.File brainFolder = new java.io.File(brainDir);
                if (!brainFolder.exists() || !brainFolder.isDirectory()) {
                    return Result.error("模型目录不存在: " + brainDir);
                }
                java.io.File[] subDirs = brainFolder.listFiles(java.io.File::isDirectory);
                if (subDirs != null) {
                    for (java.io.File sd : subDirs) {
                        addModelsFromDir(sd, models);
                    }
                }
            }

            // 按名称排序
            models.sort(java.util.Comparator.comparing(m -> m.get("label")));

            return Result.success(models);
        } catch (Exception e) {
            log.error("获取模型列表失败", e);
            return Result.error("获取模型列表失败: " + e.getMessage());
        }
    }

    /**
     * 从指定目录递归扫描 .pt 模型文件
     * - 有 weights/best.pt → 用所在文件夹名作为标签（如 YOLOv8），不深入扫描
     * - 无 best.pt → 递归查找所有 .pt 文件，用父目录名作为标签
     * - 跳过 last.pt
     */
    private void addModelsFromDir(java.io.File dir, java.util.List<java.util.Map<String, String>> models) {
        java.io.File bestPt = new java.io.File(dir, "weights/best.pt");

        if (bestPt.exists()) {
            java.util.Map<String, String> model = new java.util.HashMap<>();
            model.put("label", dir.getName());
            model.put("value", bestPt.getAbsolutePath());
            models.add(model);
            return; // best.pt 已找到，无需继续递归该目录
        }

        java.io.File[] allFiles = dir.listFiles();
        if (allFiles != null) {
            for (java.io.File f : allFiles) {
                if (f.isDirectory()) {
                    addModelsFromDir(f, models);
                } else if (f.getName().endsWith(".pt") && !f.getName().equalsIgnoreCase("last.pt")) {
                    java.util.Map<String, String> model = new java.util.HashMap<>();
                    model.put("label", f.getParentFile().getName());
                    model.put("value", f.getAbsolutePath());
                    models.add(model);
                }
            }
        }
    }

    private String getPythonDetectUrl() {
        try {
            return configService.getPythonDetectUrl();
        } catch (Exception e) {
            return pythonDetectUrl;
        }
    }

    private String getPythonSegmentUrl() {
        try {
            return configService.getPythonSegmentUrl();
        } catch (Exception e) {
            return pythonSegmentUrl;
        }
    }

    private String getPythonServiceUrl(boolean segmentMode, String segmentService) {
        if (!segmentMode) {
            return getPythonDetectUrl() + "/detect";
        }
        if ("yolo".equals(segmentService)) {
            return getPythonYoloSegmentUrl() + "/detect";
        }
        return getPythonUnetSegmentUrl() + "/segment";
    }

    private String getPythonYoloSegmentUrl() {
        try {
            return configService.getPythonYoloSegmentUrl();
        } catch (Exception e) {
            return getPythonDetectUrl();
        }
    }

    private String getPythonUnetSegmentUrl() {
        try {
            return configService.getPythonUnetSegmentUrl();
        } catch (Exception e) {
            return getPythonSegmentUrl();
        }
    }

    private String normalizeSegmentService(String segmentService) {
        try {
            if (segmentService == null || segmentService.isBlank()) {
                return configService.getDefaultMaskSegmentService();
            }
            return configService.normalizeSegmentService(segmentService);
        } catch (Exception e) {
            return "unet";
        }
    }

    private String inferModelFormat(String modelPath) {
        String lowerPath = modelPath == null ? "" : modelPath.toLowerCase(Locale.ROOT);
        if (lowerPath.endsWith(".engine") || lowerPath.endsWith(".trt")) {
            return "tensorrt";
        }
        if (lowerPath.endsWith(".onnx")) {
            return "onnx";
        }
        return "pytorch";
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

            // 检查模型是否已配置
            if (!aiService.isConfigured(model)) {
                return Result.error("AI模型 '" + model + "' 未配置API Key，请联系管理员配置");
            }

            // 更新为分析中状态
            detect.setAiStatus(1);
            detect.setAiModel(model);
            detectService.update(detect);

            log.info("开始AI分析记录: {}, 模型: {}", id, model);

            String prompt = buildAnalysisPrompt(detect);
            String aiResult = cleanAiAnalysisText(aiService.analyze(model, prompt));

            detect.setAiAnalysisResult(aiResult);
            detect.setAiAnalysisTime(LocalDateTime.now());
            detect.setAiStatus(2); // 分析完成
            detectService.update(detect);

            log.info("AI分析完成: detectId={}, model={}", id, model);
            return Result.success(detect);

        } catch (Exception e) {
            log.error("AI分析失败", e);
            // 更新失败状态
            Detect detect = new Detect();
            detect.setId(id);
            detect.setAiStatus(3);
            detectService.update(detect);
            return Result.error("AI分析失败: " + e.getMessage());
        }
    }

    /**
     * 获取AI模型配置状态
     */
    @GetMapping("/aiConfigStatus")
    public Result getAiConfigStatus() {
        return Result.success(aiService.getConfigStatus());
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
            "5. 需要注意的事项\n" +
            "6. 饮食建议\n" +
            "7. 作息建议\n" +
            "8. 是否能够进行运动的建议\n\n" +
            "输出要求：请使用纯文本中文输出，不要使用Markdown格式；不要使用星号*、井号#、反引号`、项目符号等特殊格式符号；标题直接写中文标题加冒号。\n" +
            "最后必须单独输出这一句话：本分析报告仅供参考，仅为辅助分析，不做实际诊断，诊断请到正规医院进行彻底检查与分析",
            detectionData
        );
    }

    /**
     * 清理AI返回的Markdown格式符号，避免前端报告中出现 *, #, ` 等特殊标记。
     */
    private String cleanAiAnalysisText(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("\r\n", "\n")
                .replaceAll("[*#`]+", "")
                .replaceAll("(?m)^\\s*[-•]\\s+", "")
                .replaceAll("(?m)^\\s*>\\s*", "")
                .trim();
    }
}
