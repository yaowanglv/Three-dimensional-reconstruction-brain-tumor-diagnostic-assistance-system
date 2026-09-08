package com.example.springb.controller;

import com.example.springb.common.Result;
import com.example.springb.entity.DetectionLog;
import com.example.springb.service.DataviewService;
import com.example.springb.service.DetectionLogService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/dataview")
public class DataviewController {
    
    @Resource
    private DataviewService dataviewService;
    
    @Resource
    private DetectionLogService detectionLogService;
    
    /**
     * 症状类型统计（柱状图）
     */
    @GetMapping("/stats/tumorType")
    public Result getTumorTypeStats() {
        Map<String, Object> data = dataviewService.getTumorTypeStats();
        return Result.success(data);
    }
    
    /**
     * 用户预测占比（饼图）
     */
    @GetMapping("/stats/userPrediction")
    public Result getUserPredictionStats() {
        List<Map<String, Object>> data = dataviewService.getUserPredictionStats();
        return Result.success(data);
    }
    
    /**
     * 用户置信度统计（雷达图）
     */
    @GetMapping("/stats/userConfidence")
    public Result getUserConfidenceStats() {
        Map<String, Object> data = dataviewService.getUserConfidenceStats();
        return Result.success(data);
    }
    
    /**
     * 日预测趋势（曲线图）
     */
    @GetMapping("/stats/dailyTrend")
    public Result getDailyTrend(@RequestParam(defaultValue = "10") int days) {
        Map<String, Object> data = dataviewService.getDailyTrend(days);
        return Result.success(data);
    }
    
    /**
     * 实时检测日志
     */
    @GetMapping("/logs/realtime")
    public Result getRealtimeLogs(@RequestParam(defaultValue = "20") int limit) {
        List<DetectionLog> logs = detectionLogService.getRecentLogs(limit);
        return Result.success(logs);
    }
}
