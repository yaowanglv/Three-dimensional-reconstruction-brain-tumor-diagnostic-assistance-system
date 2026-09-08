package com.example.springb.service;

import com.example.springb.entity.DetectionLog;
import com.example.springb.mapper.DetectionLogMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DetectionLogService {
    
    @Resource
    private DetectionLogMapper detectionLogMapper;
    
    /**
     * 添加日志记录
     */
    public void addLog(DetectionLog log) {
        detectionLogMapper.insert(log);
    }
    
    /**
     * 获取最近N条日志
     */
    public List<DetectionLog> getRecentLogs(int limit) {
        return detectionLogMapper.selectRecentLogs(limit);
    }
}
