-- 模型扫描配置与系统配置
USE `dsecond`;

CREATE TABLE IF NOT EXISTS `model_config` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `page_type` VARCHAR(20) NOT NULL COMMENT '页面类型：detect/mask/multi',
    `folder_path` VARCHAR(500) NOT NULL COMMENT '模型文件夹路径',
    `display_name` VARCHAR(200) COMMENT '前端显示名称',
    `model_name` VARCHAR(200) NOT NULL COMMENT '模型文件名',
    `model_path` VARCHAR(500) NOT NULL COMMENT '模型完整路径',
    `model_type` VARCHAR(10) NOT NULL COMMENT '模型类型：pt/pth/onnx',
    `is_active` TINYINT DEFAULT 1 COMMENT '是否启用：0-禁用 1-启用',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_page_type` (`page_type`),
    INDEX `idx_is_active` (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型配置表';

CREATE TABLE IF NOT EXISTS `system_config` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `config_key` VARCHAR(50) NOT NULL UNIQUE COMMENT '配置键',
    `config_value` VARCHAR(500) NOT NULL COMMENT '配置值',
    `description` VARCHAR(200) COMMENT '配置说明',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

INSERT IGNORE INTO `system_config` (`config_key`, `config_value`, `description`) VALUES
('threshold.detect', '0.45', '快速诊断页面默认阈值'),
('threshold.mask', '0.45', '精准分割页面默认阈值'),
('threshold.multi', '0.25', '批量分割页面默认阈值'),
('global.backend_url', 'http://localhost:9527', '后端API地址'),
('global.python_detect_url', 'http://localhost:5001', 'Python检测服务地址'),
('global.python_segment_url', 'http://localhost:3408', 'UNet分割服务地址');
