package com.example.springb.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 数据可视化统计实体
 */
public class DataviewStats {
    
    private Integer id;
    private String statType;        // 统计类型
    private String statKey;         // 统计键
    private Double statValue;       // 统计值
    private Integer statCount;      // 计数
    private LocalDate statDate;     // 统计日期
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    
    // Getter和Setter
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    
    public String getStatType() { return statType; }
    public void setStatType(String statType) { this.statType = statType; }
    
    public String getStatKey() { return statKey; }
    public void setStatKey(String statKey) { this.statKey = statKey; }
    
    public Double getStatValue() { return statValue; }
    public void setStatValue(Double statValue) { this.statValue = statValue; }
    
    public Integer getStatCount() { return statCount; }
    public void setStatCount(Integer statCount) { this.statCount = statCount; }
    
    public LocalDate getStatDate() { return statDate; }
    public void setStatDate(LocalDate statDate) { this.statDate = statDate; }
    
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
