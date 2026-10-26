-- 排名参考优先级：可排序的决胜项队列（非选手）
-- criterion_code 例：POINTS / HEAD_TO_HEAD / NET / TOTAL_SCORE / MATCH_GE2 / MATCH_MAX / STEAL_COUNT / STEAL_MAX / USERNAME
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

-- 系列是否启用自定义排名参考优先级
ALTER TABLE `series`
  ADD COLUMN `ranking_priority_enabled` TINYINT(1) NOT NULL DEFAULT 0
    COMMENT '是否启用排名参考优先级' AFTER `name`;
