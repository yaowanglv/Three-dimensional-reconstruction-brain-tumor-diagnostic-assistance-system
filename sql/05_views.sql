-- 数据可视化视图
USE `dsecond`;

DROP VIEW IF EXISTS `v_user_confidence_stats`;
DROP VIEW IF EXISTS `v_tumor_type_stats`;
DROP VIEW IF EXISTS `v_user_prediction_stats`;
DROP VIEW IF EXISTS `v_daily_stats`;

CREATE VIEW `v_user_confidence_stats` AS
SELECT
    'user_confidence' AS stat_type,
    user_name AS stat_key,
    AVG(COALESCE(
        CAST(JSON_UNQUOTE(JSON_EXTRACT(detection_data, '$.confidence')) AS DECIMAL(5,4)),
        CAST(JSON_UNQUOTE(JSON_EXTRACT(detection_data, '$.boxes[0].confidence')) AS DECIMAL(5,4)),
        CAST(JSON_UNQUOTE(JSON_EXTRACT(detection_data, '$.polygons[0].confidence')) AS DECIMAL(5,4)),
        0
    )) AS stat_value,
    COUNT(*) AS stat_count
FROM detect
WHERE detect_status = 2 AND detection_data IS NOT NULL
GROUP BY user_id, user_name;

CREATE VIEW `v_tumor_type_stats` AS
SELECT
    'tumor_type' AS stat_type,
    CASE
        WHEN JSON_EXTRACT(detection_data, '$.tumor_type') IS NULL THEN 'normal'
        ELSE JSON_UNQUOTE(JSON_EXTRACT(detection_data, '$.tumor_type'))
    END AS stat_key,
    COUNT(*) AS stat_count
FROM detect
WHERE detect_status = 2
GROUP BY stat_key;

CREATE VIEW `v_user_prediction_stats` AS
SELECT
    'user_prediction' AS stat_type,
    user_name AS stat_key,
    COUNT(*) AS stat_count
FROM detect
WHERE detect_status = 2
GROUP BY user_id, user_name;

CREATE VIEW `v_daily_stats` AS
SELECT
    'daily' AS stat_type,
    DATE(create_time) AS stat_date,
    COUNT(*) AS stat_count
FROM detect
WHERE detect_status = 2
GROUP BY DATE(create_time);
