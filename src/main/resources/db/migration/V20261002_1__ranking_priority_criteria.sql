-- 若曾按「选手队列」建过表，重建为「决胜项队列」
DROP TABLE IF EXISTS `ranking_priority_entry`;

CREATE TABLE `ranking_priority_entry` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `criterion_code` VARCHAR(32) NOT NULL COMMENT '决胜项代码',
  `sort_order` INT NOT NULL COMMENT '越小优先级越高（降序第1）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ranking_priority_criterion` (`criterion_code`),
  KEY `idx_ranking_priority_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
