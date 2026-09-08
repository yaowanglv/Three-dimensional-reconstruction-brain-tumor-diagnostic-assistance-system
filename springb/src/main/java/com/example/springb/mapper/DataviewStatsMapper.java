package com.example.springb.mapper;

import com.example.springb.entity.DataviewStats;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface DataviewStatsMapper {
    
    /**
     * 插入统计记录
     */
    @Insert("INSERT INTO dataview_stats (stat_type, stat_key, stat_value, stat_count, stat_date) " +
            "VALUES (#{statType}, #{statKey}, #{statValue}, #{statCount}, #{statDate})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DataviewStats stats);
    
    /**
     * 根据类型查询统计
     */
    @Select("SELECT * FROM dataview_stats WHERE stat_type = #{statType} ORDER BY stat_count DESC")
    List<DataviewStats> selectByType(String statType);
    
    /**
     * 查询症状类型统计（从视图）
     */
    @Select("SELECT stat_key, stat_count FROM v_tumor_type_stats ORDER BY stat_count DESC")
    List<Map<String, Object>> selectTumorTypeStats();
    
    /**
     * 查询用户预测统计
     */
    @Select("SELECT stat_key as userName, stat_count as count FROM v_user_prediction_stats ORDER BY stat_count DESC")
    List<Map<String, Object>> selectUserPredictionStats();
    
    /**
     * 查询用户置信度统计
     */
    @Select("SELECT stat_key as userName, COALESCE(stat_value, 0) as avgConfidence FROM v_user_confidence_stats")
    List<Map<String, Object>> selectUserConfidenceStats();
    
    /**
     * 查询日预测趋势
     */
    @Select("SELECT stat_date as date, stat_count as count FROM v_daily_stats " +
            "WHERE stat_date >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) " +
            "ORDER BY stat_date ASC")
    List<Map<String, Object>> selectDailyTrend(int days);
}
