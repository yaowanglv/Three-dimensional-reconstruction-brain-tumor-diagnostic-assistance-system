package com.example.springb.mapper;

import com.example.springb.entity.ModelConfig;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface ConfigMapper {

    @Update("""
            CREATE TABLE IF NOT EXISTS model_config (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                page_type VARCHAR(20) NOT NULL COMMENT '页面类型：detect/mask/multi',
                folder_path VARCHAR(500) NOT NULL COMMENT '模型文件夹路径',
                display_name VARCHAR(200) COMMENT '前端显示名称',
                model_name VARCHAR(200) NOT NULL COMMENT '模型文件名',
                model_path VARCHAR(500) NOT NULL COMMENT '模型完整路径',
                model_type VARCHAR(10) NOT NULL COMMENT '模型类型：pt/pth/onnx',
                is_active TINYINT DEFAULT 1 COMMENT '是否启用：0-禁用 1-启用',
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_page_type (page_type),
                INDEX idx_is_active (is_active)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型配置表'
            """)
    void createModelConfigTable();

    @Update("""
            CREATE TABLE IF NOT EXISTS system_config (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                config_key VARCHAR(50) NOT NULL UNIQUE COMMENT '配置键',
                config_value VARCHAR(500) NOT NULL COMMENT '配置值',
                description VARCHAR(200) COMMENT '配置说明',
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_config_key (config_key)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表'
            """)
    void createSystemConfigTable();

    @Select("SELECT * FROM model_config WHERE page_type = #{pageType} AND is_active = 1 ORDER BY created_at DESC")
    List<ModelConfig> selectByPageType(@Param("pageType") String pageType);

    @Select("SELECT * FROM model_config WHERE page_type = #{pageType} AND model_path = #{modelPath} LIMIT 1")
    ModelConfig selectByPageTypeAndPath(@Param("pageType") String pageType, @Param("modelPath") String modelPath);

    @Insert("""
            INSERT INTO model_config (page_type, folder_path, display_name, model_name, model_path, model_type, is_active)
            VALUES (#{pageType}, #{folderPath}, #{displayName}, #{modelName}, #{modelPath}, #{modelType}, #{isActive})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertModel(ModelConfig model);

    @Update("ALTER TABLE model_config ADD COLUMN display_name VARCHAR(200) COMMENT '前端显示名称' AFTER folder_path")
    void addDisplayNameColumn();

    @Update("UPDATE model_config SET display_name = SUBSTRING_INDEX(model_name, '.', 1) WHERE display_name IS NULL OR display_name = ''")
    void backfillDisplayNames();

    @Update("UPDATE model_config SET display_name = #{displayName} WHERE id = #{id}")
    void updateDisplayName(@Param("id") Long id, @Param("displayName") String displayName);

    @Delete("DELETE FROM model_config WHERE id = #{id}")
    void deleteById(@Param("id") Long id);

    @Delete("DELETE FROM model_config WHERE page_type = #{pageType} AND folder_path = #{folderPath}")
    void deleteByPageTypeAndFolder(@Param("pageType") String pageType, @Param("folderPath") String folderPath);

    @Select("SELECT config_value FROM system_config WHERE config_key = #{configKey} LIMIT 1")
    String selectValueByKey(@Param("configKey") String configKey);

    @Delete("DELETE FROM system_config WHERE config_key = #{configKey}")
    void deleteByConfigKey(@Param("configKey") String configKey);

    @Insert("""
            INSERT INTO system_config (config_key, config_value, description)
            VALUES (#{configKey}, #{configValue}, #{description})
            ON DUPLICATE KEY UPDATE
                config_value = VALUES(config_value),
                description = COALESCE(VALUES(description), description)
            """)
    void upsertConfig(@Param("configKey") String configKey,
                      @Param("configValue") String configValue,
                      @Param("description") String description);

    @Insert("""
            INSERT IGNORE INTO system_config (config_key, config_value, description) VALUES
            ('threshold.detect', '0.45', '快速诊断页面默认阈值'),
            ('threshold.mask', '0.45', '精准分割页面默认阈值'),
            ('threshold.multi', '0.25', '批量分割页面默认阈值'),
            ('global.backend_url', 'http://localhost:9527', '后端API地址'),
            ('global.python_detect_url', 'http://localhost:5001', 'Python检测服务地址'),
            ('global.python_segment_url', 'http://localhost:3408', 'UNet分割服务地址')
            """)
    void insertDefaultConfigs();
}
