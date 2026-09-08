package com.example.springb.entity;

import java.time.LocalDateTime;

/**
 * 检测实时日志实体
 */
public class DetectionLog {
    
    private Integer id;
    private Integer detectId;       // 关联detect表ID
    private Integer userId;         // 用户ID
    private String userName;        // 用户名
    private String modelName;       // 权重名称
    private Double confThreshold;   // 置信度阈值
    private String aiModel;         // AI模型
    private String tumorType;       // 肿瘤类型
    private Double confidence;      // 检测置信度
    private LocalDateTime createTime;
    
    // Getter和Setter
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    
    public Integer getDetectId() { return detectId; }
    public void setDetectId(Integer detectId) { this.detectId = detectId; }
    
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    
    public Double getConfThreshold() { return confThreshold; }
    public void setConfThreshold(Double confThreshold) { this.confThreshold = confThreshold; }
    
    public String getAiModel() { return aiModel; }
    public void setAiModel(String aiModel) { this.aiModel = aiModel; }
    
    public String getTumorType() { return tumorType; }
    public void setTumorType(String tumorType) { this.tumorType = tumorType; }
    
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
