package com.example.springb.controller;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.example.springb.common.Result;
import com.example.springb.entity.Detect;
import com.example.springb.service.DetectService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/mesh")
public class MeshController {

    private static final Logger log = LoggerFactory.getLogger(MeshController.class);

    @Value("${python.mesh.url:http://localhost:5002}")
    private String pythonMeshUrl;

    @Resource
    private DetectService detectService;

    @PostMapping("/build")
    public Result build(@RequestBody MeshBuildRequest request) {
        try {
            if (request.getCasePath() == null || request.getCasePath().isBlank()) {
                return Result.error("请输入 casePath");
            }

            File caseDir = new File(request.getCasePath());
            if (!caseDir.exists() || !caseDir.isDirectory()) {
                return Result.error("casePath 不存在或不是文件夹: " + caseDir.getAbsolutePath());
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("casePath", caseDir.getAbsolutePath());
            payload.put("case_dir", caseDir.getAbsolutePath());
            payload.put("modality", request.getModality() == null ? "t1c" : request.getModality());
            payload.put("normalizationMode", request.getNormalizationMode() == null ? "monai" : request.getNormalizationMode());
            payload.put("normalization_mode", request.getNormalizationMode() == null ? "monai" : request.getNormalizationMode());
            payload.put("maxTotalFaces", request.getMaxTotalFaces() == null ? 300000 : request.getMaxTotalFaces());
            payload.put("brainScale", request.getBrainScale() == null ? 0.85 : request.getBrainScale());
            payload.put("tumorScale", request.getTumorScale() == null ? 1.0 : request.getTumorScale());

            HttpResponse response = HttpRequest.post(pythonMeshUrl + "/mesh/build")
                    .header("Content-Type", "application/json;charset=utf-8")
                    .body(JSONUtil.toJsonStr(payload))
                    .timeout(180000)
                    .execute();

            if (response.getStatus() < 200 || response.getStatus() >= 300) {
                return Result.error("Python Mesh 服务响应异常: " + response.getStatus());
            }

            cn.hutool.json.JSONObject json = JSONUtil.parseObj(response.body());
            int code = json.getInt("code", -1);
            if (code != 0) {
                return Result.error(json.getStr("message", "Mesh 生成失败"));
            }
            Integer recordId = Boolean.FALSE.equals(request.getSaveHistory())
                    ? null
                    : saveThreedimHistory(request, caseDir, json.getJSONObject("data"));
            Object data = json.get("data");
            if (data instanceof cn.hutool.json.JSONObject dataObject && recordId != null) {
                dataObject.set("recordId", recordId);
            }
            return Result.success(json.get("data"));
        } catch (Exception e) {
            log.error("生成 3D Mesh 失败", e);
            return Result.error("生成 3D Mesh 失败: " + e.getMessage());
        }
    }

    private Integer saveThreedimHistory(MeshBuildRequest request, File caseDir, cn.hutool.json.JSONObject meshData) {
        try {
            Detect detect = new Detect();
            detect.setUserId(request.getUserId() == null ? 1 : request.getUserId());
            detect.setUserName(request.getUserName() == null || request.getUserName().isBlank() ? "三维重建用户" : request.getUserName());
            detect.setOriginalImageName(caseDir.getName());
            detect.setOriginalImageUrl(caseDir.getAbsolutePath());
            detect.setOriginalImageSize(0L);
            detect.setOriginalImageFormat("npy");
            detect.setDetectionData(JSONUtil.toJsonStr(buildThreedimDetectionData(request, caseDir, meshData)));
            detect.setSegmentType("threedim");
            detect.setCasePath(caseDir.getAbsolutePath());
            detect.setOutputPath(caseDir.getAbsolutePath());
            detect.setTotalSlices(extractSliceCount(meshData));
            detect.setServiceType("mesh");
            detect.setDetectMode("threedim");
            detect.setDetectStatus(2);
            detect.setAiStatus(0);
            detectService.add(detect);
            return detect.getId();
        } catch (Exception e) {
            log.warn("保存三维重建历史记录失败: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> buildThreedimDetectionData(MeshBuildRequest request, File caseDir, cn.hutool.json.JSONObject meshData) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mode", "threedim");
        data.put("casePath", caseDir.getAbsolutePath());
        data.put("modality", request.getModality() == null ? "t1c" : request.getModality());
        data.put("normalizationMode", request.getNormalizationMode() == null ? "monai" : request.getNormalizationMode());
        data.put("maxTotalFaces", request.getMaxTotalFaces() == null ? 300000 : request.getMaxTotalFaces());
        data.put("brainScale", request.getBrainScale() == null ? 0.85 : request.getBrainScale());
        data.put("tumorScale", request.getTumorScale() == null ? 1.0 : request.getTumorScale());
        data.put("stats", meshData == null ? null : meshData.get("stats"));
        return data;
    }

    private Integer extractSliceCount(cn.hutool.json.JSONObject meshData) {
        if (meshData == null || meshData.getJSONObject("stats") == null) {
            return null;
        }
        return meshData.getJSONObject("stats").getInt("slices", null);
    }

    @GetMapping("/health")
    public Result health() {
        return Result.success("Mesh 服务运行正常");
    }

    @GetMapping("/pythonHealth")
    public Result pythonHealth() {
        try {
            HttpResponse response = HttpRequest.get(pythonMeshUrl + "/mesh/health")
                    .timeout(5000)
                    .execute();
            if (response.getStatus() == 200) {
                return Result.success(JSONUtil.parseObj(response.body()));
            }
            return Result.error("Python Mesh 服务响应异常: " + response.getStatus());
        } catch (Exception e) {
            return Result.error("Python Mesh 服务连接失败: " + e.getMessage());
        }
    }

    public static class MeshBuildRequest {
        private String casePath;
        private String modality;
        private String normalizationMode;
        private Integer maxTotalFaces;
        private Double brainScale;
        private Double tumorScale;
        private Integer userId;
        private String userName;
        private Boolean saveHistory;

        public String getCasePath() {
            return casePath;
        }

        public void setCasePath(String casePath) {
            this.casePath = casePath;
        }

        public String getModality() {
            return modality;
        }

        public void setModality(String modality) {
            this.modality = modality;
        }

        public String getNormalizationMode() {
            return normalizationMode;
        }

        public void setNormalizationMode(String normalizationMode) {
            this.normalizationMode = normalizationMode;
        }

        public Integer getMaxTotalFaces() {
            return maxTotalFaces;
        }

        public void setMaxTotalFaces(Integer maxTotalFaces) {
            this.maxTotalFaces = maxTotalFaces;
        }

        public Double getBrainScale() {
            return brainScale;
        }

        public void setBrainScale(Double brainScale) {
            this.brainScale = brainScale;
        }

        public Double getTumorScale() {
            return tumorScale;
        }

        public void setTumorScale(Double tumorScale) {
            this.tumorScale = tumorScale;
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

        public Boolean getSaveHistory() {
            return saveHistory;
        }

        public void setSaveHistory(Boolean saveHistory) {
            this.saveHistory = saveHistory;
        }
    }
}
