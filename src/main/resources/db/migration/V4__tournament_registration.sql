-- 赛事报名接龙：配置 + 报名记录
SET time_zone = '+8:00';

CREATE TABLE `tournament_registration_setting` (
  `tournament_id` BIGINT NOT NULL COMMENT '赛事ID',
  `enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否开启报名（仅筹备中赛事有效）',
  `deadline` DATETIME NOT NULL COMMENT '报名截止时间',
  `quota_n` INT NOT NULL DEFAULT 32 COMMENT '正赛总名额 n',
  `mode` TINYINT NOT NULL DEFAULT 0 COMMENT '0默认直通车 1正赛+资格赛',
  `main_direct_m` INT NULL COMMENT '资格赛模式下正赛直通人数 m，须 0<=m<n',
  `qualifier_seed_count` INT NULL COMMENT '资格赛种子位数，默认可用 n-m',
  `ban_total_rank_top` INT NULL COMMENT '总排名前K禁报（500/250等配置后生效）',
  `ban_other_tournament_id` BIGINT NULL COMMENT '参照的其他赛事ID',
  `ban_other_tournament_top` INT NULL COMMENT '该赛事排名前K禁报',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`tournament_id`),
  CONSTRAINT `fk_trs_tournament` FOREIGN KEY (`tournament_id`) REFERENCES `tournament` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_trs_other_tournament` FOREIGN KEY (`ban_other_tournament_id`) REFERENCES `tournament` (`id`) ON DELETE SET NULL
) COMMENT='赛事报名配置';

CREATE TABLE `tournament_registration` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tournament_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0待审 1通过 2拒绝',
  `registered_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `reviewed_at` DATETIME NULL,
  `reviewed_by_user_id` BIGINT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tr_tournament_user` (`tournament_id`, `user_id`),
  KEY `idx_tr_tournament_time` (`tournament_id`, `registered_at`),
  CONSTRAINT `fk_tr_tournament` FOREIGN KEY (`tournament_id`) REFERENCES `tournament` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_tr_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_tr_reviewer` FOREIGN KEY (`reviewed_by_user_id`) REFERENCES `user` (`id`) ON DELETE SET NULL
) COMMENT='赛事报名记录';
