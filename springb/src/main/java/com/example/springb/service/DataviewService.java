package com.example.springb.service;

import com.example.springb.entity.DataviewStats;
import com.example.springb.mapper.DataviewStatsMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DataviewService {
    
    @Resource
    private DataviewStatsMapper dataviewStatsMapper;
    
    /**
     * 获取症状类型统计数据（柱状图）
     */
    public Map<String, Object> getTumorTypeStats() {
        List<Map<String, Object>> stats = dataviewStatsMapper.selectTumorTypeStats();
        
        // 定义症状类型映射
        Map<String, String> typeMapping = new HashMap<>();
        typeMapping.put("normal", "正常");
        typeMapping.put("glioma", "神经胶质瘤");
        typeMapping.put("meningioma", "脑膜瘤");
        typeMapping.put("pituitary", "垂体瘤");
        
        List<String> categories = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        
        // 初始化默认值
        Map<String, Integer> defaultStats = new LinkedHashMap<>();
        defaultStats.put("正常", 0);
        defaultStats.put("神经胶质瘤", 0);
        defaultStats.put("脑膜瘤", 0);
        defaultStats.put("垂体瘤", 0);
        
        // 填充实际数据（只保留有效的标签类型）
        for (Map<String, Object> stat : stats) {
            String key = (String) stat.get("stat_key");
            // 跳过已废弃的标签
            if ("tumor".equals(key) || "space_occupying".equals(key)) {
                continue;
            }
            Integer count = ((Number) stat.get("stat_count")).intValue();
            String cnName = typeMapping.getOrDefault(key, key);
            defaultStats.put(cnName, count);
        }
        
        for (Map.Entry<String, Integer> entry : defaultStats.entrySet()) {
            categories.add(entry.getKey());
            values.add(entry.getValue());
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("categories", categories);
        result.put("values", values);
        return result;
    }
    
    /**
     * 获取用户预测占比（饼图）
     */
    public List<Map<String, Object>> getUserPredictionStats() {
        List<Map<String, Object>> stats = dataviewStatsMapper.selectUserPredictionStats();
        List<Map<String, Object>> result = new ArrayList<>();
        
        int total = stats.stream().mapToInt(s -> ((Number) s.get("count")).intValue()).sum();
        
        for (Map<String, Object> stat : stats) {
            Map<String, Object> item = new HashMap<>();
            String userName = (String) stat.get("userName");
            Integer count = ((Number) stat.get("count")).intValue();
            double percentage = total > 0 ? Math.round(count * 100.0 / total * 10) / 10.0 : 0;
            
            item.put("userName", userName);
            item.put("count", count);
            item.put("percentage", percentage);
            result.add(item);
        }
        
        return result;
    }
    
    /**
     * 获取用户置信度统计（雷达图）
     */
    public Map<String, Object> getUserConfidenceStats() {
        List<Map<String, Object>> stats = dataviewStatsMapper.selectUserConfidenceStats();
        
        List<Map<String, Object>> indicators = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        
        for (Map<String, Object> stat : stats) {
            Object userNameValue = getMapValue(stat, "userName", "username", "stat_key");
            String userName = userNameValue == null ? "未知用户" : userNameValue.toString();
            double avgConfidence = toDouble(getMapValue(stat, "avgConfidence", "avgconfidence", "stat_value"), 0.0) * 100;
            
            Map<String, Object> indicator = new HashMap<>();
            indicator.put("name", userName);
            indicator.put("max", 100);
            indicators.add(indicator);
            
            values.add(Math.round(avgConfidence * 100) / 100.0);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("indicators", indicators);
        result.put("values", values);
        return result;
    }
    
    /**
     * 获取日预测趋势（曲线图）
     */
    public Map<String, Object> getDailyTrend(int days) {
        List<Map<String, Object>> stats = dataviewStatsMapper.selectDailyTrend(days);
        
        List<String> dates = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        
        // 填充最近N天数据
        Calendar cal = Calendar.getInstance();
        for (int i = days - 1; i >= 0; i--) {
            Calendar tempCal = (Calendar) cal.clone();
            tempCal.add(Calendar.DATE, -i);
            String dateStr = String.format("%02d-%02d", 
                tempCal.get(Calendar.MONTH) + 1,
                tempCal.get(Calendar.DAY_OF_MONTH));
            dates.add(dateStr);
            counts.add(0);
        }
        
        // 填充实际数据
        for (Map<String, Object> stat : stats) {
            java.sql.Date sqlDate = (java.sql.Date) stat.get("date");
            Calendar statCal = Calendar.getInstance();
            statCal.setTime(sqlDate);
            String dateStr = String.format("%02d-%02d",
                statCal.get(Calendar.MONTH) + 1,
                statCal.get(Calendar.DAY_OF_MONTH));
            
            int index = dates.indexOf(dateStr);
            if (index >= 0) {
                counts.set(index, ((Number) stat.get("count")).intValue());
            }
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("dates", dates);
        result.put("counts", counts);
        return result;
    }

    private static Object getMapValue(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key)) {
                return row.get(key);
            }
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            for (String key : keys) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private static double toDouble(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
