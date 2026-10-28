-- 单届赛事可独立设置小组排名决胜项顺序，不影响其他赛事 / 系列默认队列

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'tournament'
    AND COLUMN_NAME = 'ranking_priority_override'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `tournament` ADD COLUMN `ranking_priority_override` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否使用本届独立排名优先级'' AFTER `custom_dead_stone_limit`',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `tournament_ranking_priority_entry` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tournament_id` BIGINT NOT NULL COMMENT '赛事ID',
  `criterion_code` VARCHAR(32) NOT NULL COMMENT '决胜项代码',
  `sort_order` INT NOT NULL COMMENT '越小优先级越高（降序第1）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trp_tournament_criterion` (`tournament_id`, `criterion_code`),
  KEY `idx_trp_tournament_order` (`tournament_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
