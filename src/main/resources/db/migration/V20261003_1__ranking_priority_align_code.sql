-- 与当前代码对齐（幂等）：
-- 1) series.ranking_priority_enabled
-- 2) ranking_priority_entry 为决胜项队列（非选手）
-- 3) 空表时写入代码默认决胜顺序

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'series'
    AND COLUMN_NAME = 'ranking_priority_enabled'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `series` ADD COLUMN `ranking_priority_enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否启用排名参考优先级'' AFTER `name`',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @need_rebuild := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ranking_priority_entry') = 0
    OR (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ranking_priority_entry' AND COLUMN_NAME = 'user_id') > 0
    OR (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ranking_priority_entry' AND COLUMN_NAME = 'criterion_code') = 0,
    1, 0)
);
SET @sql := IF(@need_rebuild = 1, 'DROP TABLE IF EXISTS `ranking_priority_entry`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `ranking_priority_entry` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `criterion_code` VARCHAR(32) NOT NULL COMMENT '决胜项代码',
  `sort_order` INT NOT NULL COMMENT '越小优先级越高（降序第1）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ranking_priority_criterion` (`criterion_code`),
  KEY `idx_ranking_priority_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 仅空表时写入默认顺序：积分 → 交锋 → 净胜分 → 总分 → ≥2局数 → 单局最高 → 偷分次数 → 偷分最高 → 用户名
SET @cnt := (SELECT COUNT(*) FROM `ranking_priority_entry`);
INSERT INTO `ranking_priority_entry` (`criterion_code`, `sort_order`)
SELECT 'POINTS', 1 WHERE @cnt = 0
UNION ALL SELECT 'HEAD_TO_HEAD', 2 WHERE @cnt = 0
UNION ALL SELECT 'NET', 3 WHERE @cnt = 0
UNION ALL SELECT 'TOTAL_SCORE', 4 WHERE @cnt = 0
UNION ALL SELECT 'MATCH_GE2', 5 WHERE @cnt = 0
UNION ALL SELECT 'MATCH_MAX', 6 WHERE @cnt = 0
UNION ALL SELECT 'STEAL_COUNT', 7 WHERE @cnt = 0
UNION ALL SELECT 'STEAL_MAX', 8 WHERE @cnt = 0
UNION ALL SELECT 'USERNAME', 9 WHERE @cnt = 0;
