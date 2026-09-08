package com.example.springb.controller;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.example.springb.common.Result;
import com.example.springb.entity.Detect;
import com.example.springb.service.ConfigService;
import com.example.springb.service.DetectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/multi")
public class MultiController {

    private static final Logger log = LoggerFactory.getLogger(MultiController.class);
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "bmp", "tif", "tiff", "dcm", "npy");
    private static final DateTimeFormatter JOB_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final Map<String, BatchJobState> jobs = new ConcurrentHashMap<>();

    @Value("${python.segment.url:http://localhost:3408}")
    private String pythonSegmentUrl;

    @Value("${app.public-backend-url:http://localhost:9527}")
    private String publicBackendUrl;

    @Value("${app.batch-output-root:uploads/batch}")
    private String batchOutputRoot;

    @Resource
    private ConfigService configService;

    @Resource
    private DetectService detectService;

    @PostMapping("/startBatch")
    public Result startBatch(@RequestBody BatchStartRequest request) {
        BatchJobState job = null;
        try {
            if (request.getFolderPath() == null || request.getFolderPath().isBlank()) {
                return Result.error("请输入 MRI 切片文件夹路径");
            }

            File folder = new File(request.getFolderPath());
            if (!folder.exists() || !folder.isDirectory()) {
                return Result.error("文件夹不存在: " + folder.getAbsolutePath());
            }

            int totalFiles = countImageFiles(folder);
            if (totalFiles <= 0) {
                return Result.error("文件夹中未找到可处理的图像文件");
            }

            String jobId = "batch_" + LocalDateTime.now().format(JOB_TIME_FORMAT) + "_" + UUID.randomUUID().toString().substring(0, 6);
            String outputPath = normalizeOutputPath(request.getOutputPath(), jobId);
            File outputDir = new File(outputPath);
            if (!outputDir.exists() && !outputDir.mkdirs()) {
                return Result.error("创建输出目录失败: " + outputDir.getAbsolutePath());
            }

            job = new BatchJobState();
            job.setJobId(jobId);
            job.setFolderPath(folder.getAbsolutePath());
            job.setOutputPath(outputDir.getAbsolutePath());
            job.setModelPath(request.getModelPath());
            job.setConfidence(request.getConfidence() == null ? configService.getThreshold("multi", 0.25) : request.getConfidence());
            job.setUserId(request.getUserId());
            job.setUserName(request.getUserName());
            job.setTotalFiles(totalFiles);
            job.setStatus("processing");
            job.setStartedAt(LocalDateTime.now());
            Detect record = createBatchDetectRecord(job, request);
            job.setRecordId(record.getId());
            jobs.put(jobId, job);

            Map<String, Object> pythonPayload = new LinkedHashMap<>();
            pythonPayload.put("jobId", jobId);
            pythonPayload.put("folderPath", folder.getAbsolutePath());
            pythonPayload.put("outputPath", outputDir.getAbsolutePath());
            pythonPayload.put("pt_path", request.getModelPath());
            pythonPayload.put("modelPath", request.getModelPath());
            pythonPayload.put("conf", job.getConfidence());
            pythonPayload.put("confidence", job.getConfidence());
            pythonPayload.put("input_size", request.getInputSize() == null ? 240 : request.getInputSize());
            pythonPayload.put("n_channels", request.getNChannels() == null ? 3 : request.getNChannels());
            pythonPayload.put("model_format", inferModelFormat(request.getModelPath()));
            pythonPayload.put("callbackUrl", getPublicBackendUrl() + "/multi/progressCallback");

            HttpResponse response = HttpRequest.post(getPythonSegmentUrl() + "/segment/batch")
                    .header("Content-Type", "application/json;charset=utf-8")
                    .body(JSONUtil.toJsonStr(pythonPayload))
                    .timeout(10000)
                    .execute();

            if (response.getStatus() < 200 || response.getStatus() >= 300) {
                job.setStatus("failed");
                job.setErrorMessage("Python 批量分割服务响应异常: " + response.getStatus());
                job.setFinishedAt(LocalDateTime.now());
                updateBatchDetectRecord(job);
                return Result.error(job.getErrorMessage());
            }

            cn.hutool.json.JSONObject jsonResponse = JSONUtil.parseObj(response.body());
            if (jsonResponse.getInt("code", -1) != 0) {
                job.setStatus("failed");
                job.setErrorMessage(jsonResponse.getStr("message", "Python 批量分割服务启动失败"));
                job.setFinishedAt(LocalDateTime.now());
                updateBatchDetectRecord(job);
                return Result.error(job.getErrorMessage());
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("jobId", jobId);
            data.put("totalFiles", totalFiles);
            data.put("status", job.getStatus());
            data.put("outputPath", job.getOutputPath());
            data.put("recordId", job.getRecordId());
            return Result.success(data);
        } catch (Exception e) {
            log.error("启动批量分割失败", e);
            if (job != null) {
                job.setStatus("failed");
                job.setErrorMessage(e.getMessage());
                job.setFinishedAt(LocalDateTime.now());
                updateBatchDetectRecord(job);
            }
            return Result.error("启动批量分割失败: " + e.getMessage());
        }
    }

    @GetMapping("/progress/{jobId}")
    public Result getProgress(@PathVariable String jobId) {
        BatchJobState job = jobs.get(jobId);
        if (job == null) {
            return Result.error("任务不存在: " + jobId);
        }
        refreshFromUnet(job);
        return Result.success(job.toProgressMap());
    }

    @PostMapping("/caseResults")
    public Result getCaseResults(@RequestBody CaseResultsRequest request) {
        try {
            String outputPath = resolveCaseOutputPath(request);
            if (outputPath == null || outputPath.isBlank()) {
                return Result.error("缺少病例分割输出目录");
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("outputPath", outputPath);

            HttpResponse response = HttpRequest.post(getPythonSegmentUrl() + "/segment/caseResults")
                    .header("Content-Type", "application/json;charset=utf-8")
                    .body(JSONUtil.toJsonStr(payload))
                    .timeout(30000)
                    .execute();

            if (response.getStatus() < 200 || response.getStatus() >= 300) {
                return Result.error("Python 病例分割预览服务响应异常: " + response.getStatus());
            }

            cn.hutool.json.JSONObject jsonResponse = JSONUtil.parseObj(response.body());
            if (jsonResponse.getInt("code", -1) != 0) {
                return Result.error(jsonResponse.getStr("message", "恢复病例分割预览失败"));
            }

            return Result.success(jsonResponse.get("data"));
        } catch (Exception e) {
            log.warn("恢复病例分割预览失败: {}", e.getMessage());
            return Result.error("恢复病例分割预览失败: " + e.getMessage());
        }
    }

    @PostMapping("/validatePath")
    public Result validatePath(@RequestBody Map<String, String> request) {
        String path = request.getOrDefault("path", request.get("folderPath"));
        if (path == null || path.isBlank()) {
            return Result.error("路径不能为空");
        }

        File folder = new File(path);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("path", folder.getAbsolutePath());
        data.put("exists", folder.exists());
        data.put("directory", folder.isDirectory());
        data.put("totalFiles", folder.exists() && folder.isDirectory() ? countImageFiles(folder) : 0);
        data.put("readable", folder.canRead());
        return Result.success(data);
    }

    private String resolveCaseOutputPath(CaseResultsRequest request) {
        String outputPath = request == null ? null : request.getOutputPath();
        if (outputPath != null && !outputPath.isBlank()) {
            return outputPath;
        }
        if (request == null || request.getRecordId() == null) {
            return "";
        }

        Detect detect = detectService.selectById(request.getRecordId());
        if (detect == null) {
            return "";
        }

        outputPath = firstNonBlank(detect.getCasePath(), detect.getOutputPath());
        if (outputPath != null && !outputPath.isBlank()) {
            return outputPath;
        }

        try {
            cn.hutool.json.JSONObject data = JSONUtil.parseObj(detect.getDetectionData());
            return firstNonBlank(data.getStr("casePath"), data.getStr("outputPath"));
        } catch (Exception e) {
            return "";
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    @PostMapping("/stopBatch")
    public Result stopBatch(@RequestBody Map<String, String> request) {
        String jobId = request.get("jobId");
        if (jobId == null || jobId.isBlank()) {
            return Result.error("jobId 不能为空");
        }
        BatchJobState job = jobs.get(jobId);
        if (job == null) {
            return Result.error("任务不存在: " + jobId);
        }
        try {
            HttpRequest.post(getPythonSegmentUrl() + "/segment/stop")
                    .header("Content-Type", "application/json;charset=utf-8")
                    .body(JSONUtil.toJsonStr(Map.of("jobId", jobId)))
                    .timeout(5000)
                    .execute();
        } catch (Exception e) {
            log.warn("转发停止批量分割任务失败: {}", e.getMessage());
        } finally {
            job.setStatus("stopped");
            job.setFinishedAt(LocalDateTime.now());
            updateBatchDetectRecord(job);
        }
        return Result.success(job.toProgressMap());
    }

    @PostMapping("/progressCallback")
    public Result progressCallback(@RequestBody BatchProgressCallback callback) {
        if (callback.getJobId() == null || callback.getJobId().isBlank()) {
            return Result.error("jobId 不能为空");
        }

        BatchJobState job = jobs.computeIfAbsent(callback.getJobId(), BatchJobState::new);
        if (callback.getTotalFiles() != null) {
            job.setTotalFiles(callback.getTotalFiles());
        }
        if (callback.getProcessedFiles() != null) {
            job.setProcessedFiles(callback.getProcessedFiles());
        }
        if (callback.getCurrentFile() != null) {
            job.setCurrentFile(callback.getCurrentFile());
        }
        if (callback.getOutputPath() != null) {
            job.setOutputPath(callback.getOutputPath());
        }
        if (callback.getStatus() != null) {
            job.setStatus(callback.getStatus());
        }
        if (callback.getErrorMessage() != null) {
            job.setErrorMessage(callback.getErrorMessage());
        }
        if ("completed".equalsIgnoreCase(job.getStatus()) && callback.getResults() != null) {
            job.setResults(callback.getResults());
        } else if ("completed".equalsIgnoreCase(job.getStatus()) && callback.getResult() != null) {
            job.upsertResult(callback.getResult());
        }

        if ("completed".equalsIgnoreCase(job.getStatus())
                || "failed".equalsIgnoreCase(job.getStatus())
                || "stopped".equalsIgnoreCase(job.getStatus())) {
            job.setFinishedAt(LocalDateTime.now());
            updateBatchDetectRecord(job);
        }

        return Result.success(job.toProgressMap());
    }

    @GetMapping("/listModels")
    public Result listModels(@RequestParam(required = false, defaultValue = "mask") String dir) {
        try {
            String brainDir = "D:/algorithms/V11-dmt/runs/brain";
            List<Map<String, String>> models = new ArrayList<>();
            File targetDir = new File(brainDir, dir == null || dir.isBlank() ? "" : dir);
            if (!targetDir.exists() || !targetDir.isDirectory()) {
                return Result.success(models);
            }
            addModelsFromDir(targetDir, models);
            models.sort(Comparator.comparing(m -> m.get("label")));
            return Result.success(models);
        } catch (Exception e) {
            log.error("获取批量分割模型列表失败", e);
            return Result.error("获取模型列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/selectPage")
    public Result selectPage() {
        List<Map<String, Object>> data = jobs.values().stream()
                .sorted(Comparator.comparing(BatchJobState::getStartedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(BatchJobState::toMap)
                .toList();
        return Result.success(data);
    }

    @GetMapping("/health")
    public Result health() {
        return Result.success("Multi 服务运行正常");
    }

    private String normalizeOutputPath(String outputPath, String jobId) {
        if (outputPath != null && !outputPath.isBlank()) {
            return outputPath;
        }
        File root = new File(batchOutputRoot);
        if (!root.isAbsolute()) {
            root = new File(System.getProperty("user.dir"), batchOutputRoot);
        }
        return new File(root, jobId).getAbsolutePath();
    }

    private int countImageFiles(File folder) {
        File[] files = folder.listFiles(file -> file.isFile() && isImageFile(file.getName()));
        return files == null ? 0 : files.length;
    }

    private boolean isImageFile(String name) {
        String ext = "";
        int index = name.lastIndexOf('.');
        if (index >= 0 && index < name.length() - 1) {
            ext = name.substring(index + 1).toLowerCase(Locale.ROOT);
        }
        return IMAGE_EXTENSIONS.contains(ext);
    }

    private void addModelsFromDir(File dir, List<Map<String, String>> models) {
        File bestPt = new File(dir, "weights/best.pt");
        if (bestPt.exists()) {
            Map<String, String> model = new HashMap<>();
            model.put("label", dir.getName());
            model.put("value", bestPt.getAbsolutePath());
            models.add(model);
            return;
        }

        File[] allFiles = dir.listFiles();
        if (allFiles == null) {
            return;
        }
        for (File file : allFiles) {
            if (file.isDirectory()) {
                addModelsFromDir(file, models);
            } else if (file.getName().toLowerCase(Locale.ROOT).endsWith(".pt")
                    && !file.getName().equalsIgnoreCase("last.pt")) {
                Map<String, String> model = new HashMap<>();
                model.put("label", file.getParentFile().getName());
                model.put("value", file.getAbsolutePath());
                models.add(model);
            }
        }
    }

    private String getPythonSegmentUrl() {
        try {
            return configService.getPythonSegmentUrl();
        } catch (Exception e) {
            return pythonSegmentUrl;
        }
    }

    private String getPublicBackendUrl() {
        try {
            return configService.getBackendUrl();
        } catch (Exception e) {
            return publicBackendUrl;
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

    private void refreshFromUnet(BatchJobState job) {
        if (job == null || job.getJobId() == null || job.getJobId().isBlank()) {
            return;
        }
        if ("completed".equalsIgnoreCase(job.getStatus())
                || "failed".equalsIgnoreCase(job.getStatus())
                || "stopped".equalsIgnoreCase(job.getStatus())) {
            return;
        }

        try {
            HttpResponse response = HttpRequest.get(getPythonSegmentUrl() + "/segment/progress/" + job.getJobId())
                    .timeout(5000)
                    .execute();
            if (response.getStatus() < 200 || response.getStatus() >= 300) {
                return;
            }
            cn.hutool.json.JSONObject jsonResponse = JSONUtil.parseObj(response.body());
            if (jsonResponse.getInt("code", -1) != 0) {
                return;
            }
            cn.hutool.json.JSONObject data = jsonResponse.getJSONObject("data");
            if (data == null) {
                return;
            }
            if (data.containsKey("status")) {
                job.setStatus(data.getStr("status"));
            }
            if (data.containsKey("totalFiles")) {
                job.setTotalFiles(data.getInt("totalFiles", job.getTotalFiles()));
            }
            if (data.containsKey("processedFiles")) {
                job.setProcessedFiles(data.getInt("processedFiles", job.getProcessedFiles()));
            }
            if (data.containsKey("currentFile")) {
                job.setCurrentFile(data.getStr("currentFile", ""));
            }
            if (data.containsKey("outputPath")) {
                job.setOutputPath(data.getStr("outputPath", job.getOutputPath()));
            }
            if (data.containsKey("errorMessage")) {
                job.setErrorMessage(data.getStr("errorMessage", ""));
            }
            if ("completed".equalsIgnoreCase(job.getStatus()) && data.containsKey("results")) {
                List<Map<String, Object>> results = new ArrayList<>();
                for (Object item : data.getJSONArray("results")) {
                    if (item instanceof Map<?, ?> itemMap) {
                        Map<String, Object> normalized = new LinkedHashMap<>();
                        itemMap.forEach((key, value) -> normalized.put(String.valueOf(key), value));
                        results.add(normalized);
                    }
                }
                job.setResults(results);
            }
            if ("completed".equalsIgnoreCase(job.getStatus())
                    || "failed".equalsIgnoreCase(job.getStatus())
                    || "stopped".equalsIgnoreCase(job.getStatus())) {
                job.setFinishedAt(LocalDateTime.now());
                updateBatchDetectRecord(job);
            }
        } catch (Exception e) {
            log.debug("查询UNet批量分割进度失败: {}", e.getMessage());
        }
    }

    private Detect createBatchDetectRecord(BatchJobState job, BatchStartRequest request) {
        Detect detect = new Detect();
        detect.setUserId(job.getUserId() == null ? 1 : job.getUserId());
        detect.setUserName(job.getUserName() == null || job.getUserName().isBlank() ? "批量分割用户" : job.getUserName());
        detect.setOriginalImageName(new File(job.getFolderPath()).getName());
        detect.setOriginalImageUrl(job.getFolderPath());
        detect.setOriginalImageSize(0L);
        detect.setOriginalImageFormat("folder");
        detect.setDetectionData(JSONUtil.toJsonStr(buildBatchDetectionData(job)));
        detect.setSegmentType("batch");
        detect.setCasePath(job.getOutputPath());
        detect.setOutputPath(job.getOutputPath());
        detect.setTotalSlices(job.getTotalFiles());
        detect.setServiceType("unet-multi");
        detect.setInputSize(request.getInputSize() == null ? 240 : request.getInputSize());
        detect.setModelFormat(inferModelFormat(request.getModelPath()));
        detect.setDetectMode("batch_segment");
        detect.setDetectStatus(1);
        detect.setAiStatus(0);
        detectService.add(detect);
        return detect;
    }

    private void updateBatchDetectRecord(BatchJobState job) {
        if (job.getRecordId() == null) {
            return;
        }
        try {
            Detect detect = new Detect();
            detect.setId(job.getRecordId());
            detect.setDetectStatus(toDetectStatus(job.getStatus()));
            detect.setDetectionData(JSONUtil.toJsonStr(buildBatchDetectionData(job)));
            detect.setCasePath(job.getOutputPath());
            detect.setOutputPath(job.getOutputPath());
            detect.setTotalSlices(job.getTotalFiles());
            detect.setDetectMode("batch_segment");
            detect.setSegmentType("batch");
            detectService.update(detect);
        } catch (Exception e) {
            log.warn("更新批量分割历史记录失败: recordId={}, {}", job.getRecordId(), e.getMessage());
        }
    }

    private Map<String, Object> buildBatchDetectionData(BatchJobState job) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mode", "multi");
        data.put("jobId", job.getJobId());
        data.put("folderPath", job.getFolderPath());
        data.put("casePath", job.getOutputPath());
        data.put("outputPath", job.getOutputPath());
        data.put("modelPath", job.getModelPath());
        data.put("confidence", job.getConfidence());
        data.put("totalFiles", job.getTotalFiles());
        data.put("processedFiles", job.getProcessedFiles());
        data.put("status", job.getStatus());
        data.put("errorMessage", job.getErrorMessage());
        data.put("results", summarizeResults(job.getResults()));
        return data;
    }

    private List<Map<String, Object>> summarizeResults(List<Map<String, Object>> results) {
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }
        return results.stream().map(item -> {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("index", item.get("index"));
            summary.put("fileName", item.get("fileName"));
            summary.put("status", item.get("status"));
            summary.put("labels", item.get("labels"));
            summary.put("previewBase64", item.get("previewBase64"));
            summary.put("mode", item.get("mode"));
            summary.put("inferMode", item.get("inferMode"));
            summary.put("postprocessed", item.get("postprocessed"));
            summary.put("imagePath", item.get("imagePath"));
            summary.put("maskPath", item.get("maskPath"));
            summary.put("errorMessage", item.get("errorMessage"));
            return summary;
        }).toList();
    }

    private Integer toDetectStatus(String status) {
        if ("completed".equalsIgnoreCase(status)) {
            return 2;
        }
        if ("failed".equalsIgnoreCase(status) || "stopped".equalsIgnoreCase(status)) {
            return 3;
        }
        return 1;
    }

    public static class BatchStartRequest {
        private String folderPath;
        private String outputPath;
        private String modelPath;
        private Double confidence;
        private Integer userId;
        private String userName;
        private Integer inputSize;
        private Integer nChannels;

        public String getFolderPath() {
            return folderPath;
        }

        public void setFolderPath(String folderPath) {
            this.folderPath = folderPath;
        }

        public String getOutputPath() {
            return outputPath;
        }

        public void setOutputPath(String outputPath) {
            this.outputPath = outputPath;
        }

        public String getModelPath() {
            return modelPath;
        }

        public void setModelPath(String modelPath) {
            this.modelPath = modelPath;
        }

        public Double getConfidence() {
            return confidence;
        }

        public void setConfidence(Double confidence) {
            this.confidence = confidence;
        }

        public Integer getUserId() {
            return userId;
        }

        public void setUserId(Integer userId) {
            this.userId = userId;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public Integer getInputSize() {
            return inputSize;
        }

        public void setInputSize(Integer inputSize) {
            this.inputSize = inputSize;
        }

        public Integer getNChannels() {
            return nChannels;
        }

        public void setNChannels(Integer nChannels) {
            this.nChannels = nChannels;
        }
    }

    public static class CaseResultsRequest {
        private Integer recordId;
        private String outputPath;

        public Integer getRecordId() {
            return recordId;
        }

        public void setRecordId(Integer recordId) {
            this.recordId = recordId;
        }

        public String getOutputPath() {
            return outputPath;
        }

        public void setOutputPath(String outputPath) {
            this.outputPath = outputPath;
        }
    }

    public static class BatchProgressCallback {
        private String jobId;
        private String status;
        private Integer totalFiles;
        private Integer processedFiles;
        private String currentFile;
        private String outputPath;
        private String errorMessage;
        private Map<String, Object> result;
        private List<Map<String, Object>> results;

        public String getJobId() {
            return jobId;
        }

        public void setJobId(String jobId) {
            this.jobId = jobId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Integer getTotalFiles() {
            return totalFiles;
        }

        public void setTotalFiles(Integer totalFiles) {
            this.totalFiles = totalFiles;
        }

        public Integer getProcessedFiles() {
            return processedFiles;
        }

        public void setProcessedFiles(Integer processedFiles) {
            this.processedFiles = processedFiles;
        }

        public String getCurrentFile() {
            return currentFile;
        }

        public void setCurrentFile(String currentFile) {
            this.currentFile = currentFile;
        }

        public String getOutputPath() {
            return outputPath;
        }

        public void setOutputPath(String outputPath) {
            this.outputPath = outputPath;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public Map<String, Object> getResult() {
            return result;
        }

        public void setResult(Map<String, Object> result) {
            this.result = result;
        }

        public List<Map<String, Object>> getResults() {
            return results;
        }

        public void setResults(List<Map<String, Object>> results) {
            this.results = results;
        }
    }

    public static class BatchJobState {
        private String jobId;
        private String folderPath;
        private String outputPath;
        private String modelPath;
        private Double confidence = 0.25;
        private Integer userId;
        private String userName;
        private Integer recordId;
        private Integer totalFiles = 0;
        private Integer processedFiles = 0;
        private String currentFile = "";
        private String status = "pending";
        private String errorMessage = "";
        private LocalDateTime startedAt = LocalDateTime.now();
        private LocalDateTime finishedAt;
        private List<Map<String, Object>> results = new ArrayList<>();

        public BatchJobState() {
        }

        public BatchJobState(String jobId) {
            this.jobId = jobId;
        }

        public void upsertResult(Map<String, Object> result) {
            Object fileName = result.get("fileName");
            if (fileName == null) {
                results.add(result);
                return;
            }
            for (int i = 0; i < results.size(); i++) {
                if (Objects.equals(fileName, results.get(i).get("fileName"))) {
                    results.set(i, result);
                    return;
                }
            }
            results.add(result);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("jobId", jobId);
            map.put("folderPath", folderPath);
            map.put("outputPath", outputPath);
            map.put("modelPath", modelPath);
            map.put("confidence", confidence);
            map.put("userId", userId);
            map.put("userName", userName);
            map.put("recordId", recordId);
            map.put("totalFiles", totalFiles);
            map.put("processedFiles", processedFiles);
            map.put("currentFile", currentFile);
            map.put("status", status);
            map.put("errorMessage", errorMessage);
            map.put("progress", totalFiles == null || totalFiles == 0 ? 0 : Math.round(processedFiles * 100.0 / totalFiles));
            map.put("startedAt", startedAt == null ? null : startedAt.toString());
            map.put("finishedAt", finishedAt == null ? null : finishedAt.toString());
            map.put("elapsedSeconds", startedAt == null ? 0 : Duration.between(startedAt, finishedAt == null ? LocalDateTime.now() : finishedAt).toSeconds());
            map.put("results", results);
            return map;
        }

        public Map<String, Object> toProgressMap() {
            Map<String, Object> map = toMap();
            if (!"completed".equalsIgnoreCase(status)) {
                map.put("results", Collections.emptyList());
            }
            return map;
        }

        public String getJobId() {
            return jobId;
        }

        public void setJobId(String jobId) {
            this.jobId = jobId;
        }

        public String getFolderPath() {
            return folderPath;
        }

        public void setFolderPath(String folderPath) {
            this.folderPath = folderPath;
        }

        public String getOutputPath() {
            return outputPath;
        }

        public void setOutputPath(String outputPath) {
            this.outputPath = outputPath;
        }

        public String getModelPath() {
            return modelPath;
        }

        public void setModelPath(String modelPath) {
            this.modelPath = modelPath;
        }

        public Double getConfidence() {
            return confidence;
        }

        public void setConfidence(Double confidence) {
            this.confidence = confidence;
        }

        public Integer getUserId() {
            return userId;
        }

        public void setUserId(Integer userId) {
            this.userId = userId;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public Integer getRecordId() {
            return recordId;
        }

        public void setRecordId(Integer recordId) {
            this.recordId = recordId;
        }

        public Integer getTotalFiles() {
            return totalFiles;
        }

        public void setTotalFiles(Integer totalFiles) {
            this.totalFiles = totalFiles;
        }

        public Integer getProcessedFiles() {
            return processedFiles;
        }

        public void setProcessedFiles(Integer processedFiles) {
            this.processedFiles = processedFiles;
        }

        public String getCurrentFile() {
            return currentFile;
        }

        public void setCurrentFile(String currentFile) {
            this.currentFile = currentFile;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public LocalDateTime getStartedAt() {
            return startedAt;
        }

        public void setStartedAt(LocalDateTime startedAt) {
            this.startedAt = startedAt;
        }

        public LocalDateTime getFinishedAt() {
            return finishedAt;
        }

        public void setFinishedAt(LocalDateTime finishedAt) {
            this.finishedAt = finishedAt;
        }

        public List<Map<String, Object>> getResults() {
            return results;
        }

        public void setResults(List<Map<String, Object>> results) {
            this.results = results == null ? new ArrayList<>() : results;
        }
    }
}
