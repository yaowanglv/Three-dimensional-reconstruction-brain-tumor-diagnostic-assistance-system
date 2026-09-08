-- 检测记录与 UNet 扩展字段
USE `dsecond`;

CREATE TABLE IF NOT EXISTS `detect` (
    `id` INT PRIMARY KEY AUTO_INCREMENT COMMENT '检测记录ID',
    `user_id` INT NOT NULL COMMENT '操作用户ID',
    `user_name` VARCHAR(50) COMMENT '操作用户名',
    `original_image_name` VARCHAR(255) COMMENT '原始图像文件名',
    `original_image_url` VARCHAR(500) NOT NULL COMMENT '原始图像存储路径/URL',
    `original_image_size` BIGINT COMMENT '原始图像大小(字节)',
    `original_image_format` VARCHAR(10) COMMENT '原始图像格式(jpg/png/dicom等)',
    `result_image_url` VARCHAR(500) COMMENT '检测结果图像路径/URL',
    `detection_data` JSON COMMENT '检测结果数据: 包含检测框坐标、置信度、肿瘤类型等',
    `ai_model` VARCHAR(50) COMMENT '使用的大模型(deepseek/glm/kimi)',
    `ai_analysis_result` TEXT COMMENT 'AI大模型分析结果文本',
    `ai_analysis_time` DATETIME COMMENT 'AI分析完成时间',
    `detect_status` TINYINT DEFAULT 0 COMMENT '检测状态: 0-待检测 1-检测中 2-检测完成 3-检测失败',
    `ai_status` TINYINT DEFAULT 0 COMMENT 'AI分析状态: 0-未分析 1-分析中 2-分析完成 3-分析失败',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间',
    `remark` VARCHAR(500) COMMENT '备注信息',
    `segment_type` VARCHAR(20) DEFAULT NULL COMMENT '分割类型: single(单图分割), batch(病例分割)',
    `case_path` VARCHAR(500) DEFAULT NULL COMMENT '病例路径(批量分割时)',
    `output_path` VARCHAR(500) DEFAULT NULL COMMENT '输出路径',
    `total_slices` INT DEFAULT NULL COMMENT '总切片数(批量分割时)',
    `service_type` VARCHAR(20) DEFAULT 'yolo' COMMENT '服务类型: yolo, unet-mask, unet-multi',
    `input_size` INT DEFAULT NULL COMMENT '输入尺寸',
    `model_format` VARCHAR(20) DEFAULT 'pytorch' COMMENT '模型格式: pytorch, tensorrt, onnx',
    `detect_mode` VARCHAR(20) DEFAULT 'detect' COMMENT '病灶分析类别: detect/segment/batch_segment/threedim',
    INDEX `idx_detect_user_id` (`user_id`),
    INDEX `idx_detect_status` (`detect_status`),
    INDEX `idx_detect_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='脑肿瘤检测记录表';

CREATE TABLE IF NOT EXISTS `unet_model_config` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `model_name` VARCHAR(100) NOT NULL COMMENT '模型名称',
    `model_path` VARCHAR(500) NOT NULL COMMENT '模型文件路径',
    `model_type` VARCHAR(20) NOT NULL COMMENT '模型类型: mask, multi',
    `model_format` VARCHAR(20) DEFAULT 'pytorch' COMMENT '模型格式: pytorch, tensorrt, onnx',
    `precision` VARCHAR(10) DEFAULT 'fp32' COMMENT '推理精度: fp32, fp16, int8',
    `n_channels` INT DEFAULT 3 COMMENT '输入通道数',
    `n_classes` INT DEFAULT 4 COMMENT '输出类别数',
    `input_size` INT DEFAULT 512 COMMENT '输入尺寸',
    `is_default` TINYINT(1) DEFAULT 0 COMMENT '是否为默认模型',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_model_type` (`model_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='UNet模型配置表';
