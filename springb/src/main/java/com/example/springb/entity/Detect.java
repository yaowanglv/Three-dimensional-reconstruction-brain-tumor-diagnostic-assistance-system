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
    private String segmentType;
    private String casePath;
    private String outputPath;
    private Integer totalSlices;
    private String serviceType;
    private Integer inputSize;
    private String modelFormat;
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
    private String detectorName;
    private String detectorUsername;
    private String createDate;
    private String aiUsed;
    private String detectMode;

    // ============== Getter和Setter ==============

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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

    public String getOriginalImageName() {
        return originalImageName;
    }

    public void setOriginalImageName(String originalImageName) {
        this.originalImageName = originalImageName;
    }

    public String getOriginalImageUrl() {
        return originalImageUrl;
    }

    public void setOriginalImageUrl(String originalImageUrl) {
        this.originalImageUrl = originalImageUrl;
    }

    public Long getOriginalImageSize() {
        return originalImageSize;
    }

    public void setOriginalImageSize(Long originalImageSize) {
        this.originalImageSize = originalImageSize;
    }

    public String getOriginalImageFormat() {
        return originalImageFormat;
    }

    public void setOriginalImageFormat(String originalImageFormat) {
        this.originalImageFormat = originalImageFormat;
    }

    public String getResultImageUrl() {
        return resultImageUrl;
    }

    public void setResultImageUrl(String resultImageUrl) {
        this.resultImageUrl = resultImageUrl;
    }

    public String getDetectionData() {
        return detectionData;
    }

    public void setDetectionData(String detectionData) {
        this.detectionData = detectionData;
    }

    public String getSegmentType() {
        return segmentType;
    }

    public void setSegmentType(String segmentType) {
        this.segmentType = segmentType;
    }

    public String getCasePath() {
        return casePath;
    }

    public void setCasePath(String casePath) {
        this.casePath = casePath;
    }

    public String getOutputPath() {
        return outputPath;
    }

    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }

    public Integer getTotalSlices() {
        return totalSlices;
    }

    public void setTotalSlices(Integer totalSlices) {
        this.totalSlices = totalSlices;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public Integer getInputSize() {
        return inputSize;
    }

    public void setInputSize(Integer inputSize) {
        this.inputSize = inputSize;
    }

    public String getModelFormat() {
        return modelFormat;
    }

    public void setModelFormat(String modelFormat) {
        this.modelFormat = modelFormat;
    }

    public String getAiModel() {
        return aiModel;
    }

    public void setAiModel(String aiModel) {
        this.aiModel = aiModel;
    }

    public String getAiAnalysisResult() {
        return aiAnalysisResult;
    }

    public void setAiAnalysisResult(String aiAnalysisResult) {
        this.aiAnalysisResult = aiAnalysisResult;
    }

    public LocalDateTime getAiAnalysisTime() {
        return aiAnalysisTime;
    }

    public void setAiAnalysisTime(LocalDateTime aiAnalysisTime) {
        this.aiAnalysisTime = aiAnalysisTime;
    }

    public Integer getDetectStatus() {
        return detectStatus;
    }

    public void setDetectStatus(Integer detectStatus) {
        this.detectStatus = detectStatus;
    }

    public Integer getAiStatus() {
        return aiStatus;
    }

    public void setAiStatus(Integer aiStatus) {
        this.aiStatus = aiStatus;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getIds() {
        return ids;
    }

    public void setIds(String ids) {
        this.ids = ids;
    }

    public String[] getIdsArr() {
        return idsArr;
    }

    public void setIdsArr(String[] idsArr) {
        this.idsArr = idsArr;
    }

    public String getDetectorName() {
        return detectorName;
    }

    public void setDetectorName(String detectorName) {
        this.detectorName = detectorName;
    }

    public String getDetectorUsername() {
        return detectorUsername;
    }

    public void setDetectorUsername(String detectorUsername) {
        this.detectorUsername = detectorUsername;
    }

    public String getCreateDate() {
        return createDate;
    }

    public void setCreateDate(String createDate) {
        this.createDate = createDate;
    }

    public String getAiUsed() {
        return aiUsed;
    }

    public void setAiUsed(String aiUsed) {
        this.aiUsed = aiUsed;
    }

    public String getDetectMode() {
        return detectMode;
    }

    public void setDetectMode(String detectMode) {
        this.detectMode = detectMode;
    }
}
