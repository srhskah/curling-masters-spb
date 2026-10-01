-- 为每个参照赛事设置独立的前K名禁报值
SET time_zone = '+8:00';

CREATE TABLE `tournament_registration_ban_ref` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tournament_id` BIGINT NOT NULL COMMENT '当前赛事ID',
  `ref_tournament_id` BIGINT NOT NULL COMMENT '参照赛事ID',
  `ban_top` INT NOT NULL DEFAULT 1 COMMENT '该参照赛事前K名禁报（K≥1）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tournament_ref` (`tournament_id`, `ref_tournament_id`),
  KEY `idx_tournament_id` (`tournament_id`),
  KEY `idx_ref_tournament_id` (`ref_tournament_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='赛事报名禁报参照配置（每个参照赛事独立K值）';