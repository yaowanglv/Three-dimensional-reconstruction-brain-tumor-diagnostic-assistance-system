-- 用户/管理员表（仅结构，不含演示账号）
USE `dsecond`;

CREATE TABLE IF NOT EXISTS `admin` (
    `id` INT PRIMARY KEY AUTO_INCREMENT COMMENT '管理员ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(100) NOT NULL COMMENT '密码',
    `name` VARCHAR(50) COMMENT '姓名',
    `time` VARCHAR(50) COMMENT '时间',
    `role` VARCHAR(20) COMMENT '角色',
    `status` INT DEFAULT 1 COMMENT '状态: 1-启用 0-禁用',
    UNIQUE KEY `uk_admin_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员表';
