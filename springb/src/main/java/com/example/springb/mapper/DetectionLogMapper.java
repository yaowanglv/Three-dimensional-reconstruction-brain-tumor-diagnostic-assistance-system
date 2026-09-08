package com.example.springb.mapper;

import com.example.springb.entity.DetectionLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DetectionLogMapper {
    
    /**
     * 插入日志记录
     */
    @Insert("INSERT INTO detection_logs (detect_id, user_id, user_name, model_name, " +
            "conf_threshold, ai_model, tumor_type, confidence, create_time) " +
            "VALUES (#{detectId}, #{userId}, #{userName}, #{modelName}, " +
            "#{confThreshold}, #{aiModel}, #{tumorType}, #{confidence}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DetectionLog log);
    
    /**
     * 查询实时日志（最近N条），关联detect表获取最新AI模型信息
     */
    @Select("SELECT l.id, l.detect_id, l.user_id, l.user_name, l.model_name, " +
            "l.conf_threshold, COALESCE(d.ai_model, l.ai_model) as ai_model, " +
            "l.tumor_type, l.confidence, l.create_time " +
            "FROM detection_logs l " +
            "LEFT JOIN detect d ON l.detect_id = d.id " +
            "ORDER BY l.create_time DESC LIMIT #{limit}")
    List<DetectionLog> selectRecentLogs(int limit);
    
    /**
     * 根据detectId查询
     */
    @Select("SELECT * FROM detection_logs WHERE detect_id = #{detectId}")
    DetectionLog selectByDetectId(Integer detectId);
}
