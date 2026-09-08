-- 数据可视化统计与检测日志
USE `dsecond`;

CREATE TABLE IF NOT EXISTS `dataview_stats` (
    `id` INT PRIMARY KEY AUTO_INCREMENT COMMENT '统计ID',
    `stat_type` VARCHAR(50) NOT NULL COMMENT '统计类型: tumor_type/user_prediction/user_confidence/daily',
    `stat_key` VARCHAR(100) NOT NULL COMMENT '统计键',
    `stat_value` DECIMAL(10,4) COMMENT '统计值',
    `stat_count` INT DEFAULT 0 COMMENT '计数',
    `stat_date` DATE COMMENT '统计日期',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_stat_type` (`stat_type`),
    INDEX `idx_stat_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据可视化统计表';

CREATE TABLE IF NOT EXISTS `detection_logs` (
    `id` INT PRIMARY KEY AUTO_INCREMENT COMMENT '日志ID',
    `detect_id` INT NOT NULL COMMENT '关联的detect表ID',
    `user_id` INT NOT NULL COMMENT '用户ID',
    `user_name` VARCHAR(50) COMMENT '用户名',
    `model_name` VARCHAR(255) COMMENT '使用的权重名称',
    `conf_threshold` DECIMAL(3,2) COMMENT '置信度阈值',
    `ai_model` VARCHAR(50) COMMENT 'AI模型',
    `tumor_type` VARCHAR(50) COMMENT '检测出的肿瘤类型',
    `confidence` DECIMAL(5,4) COMMENT '检测置信度',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_detect_id` (`detect_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检测实时日志表';
