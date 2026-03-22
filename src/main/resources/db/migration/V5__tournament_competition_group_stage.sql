SET time_zone = '+8:00';

CREATE TABLE `tournament_competition_config` (
  `tournament_id` BIGINT NOT NULL,
  `participant_count` INT NULL COMMENT '参赛人数 m',
  `entry_mode` TINYINT NULL COMMENT '0默认模式 1正赛+资格赛',
  `match_mode` TINYINT NULL COMMENT '1单败 2双败 3小组赛',
  `group_mode` TINYINT NULL COMMENT '1单循环无主客 2单循环主客(奇数组) 3双循环主客',
  `group_size` INT NULL COMMENT '每组人数',
  `group_stage_sets` INT NULL COMMENT '小组赛每场局数',
  `knockout_stage_sets` INT NULL COMMENT '淘汰赛每场局数',
  `final_stage_sets` INT NULL COMMENT '决赛每场局数',
  `manual_locked` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否锁定配置',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`tournament_id`),
  CONSTRAINT `fk_tcc_tournament` FOREIGN KEY (`tournament_id`) REFERENCES `tournament` (`id`) ON DELETE CASCADE
) COMMENT='赛事运行配置（进行中手动指定参赛人数/赛制等）';

CREATE TABLE `tournament_group` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tournament_id` BIGINT NOT NULL,
  `group_name` VARCHAR(32) NOT NULL COMMENT 'A组/B组',
  `group_order` INT NOT NULL DEFAULT 1,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tg_tournament_name` (`tournament_id`, `group_name`),
  CONSTRAINT `fk_tg_tournament` FOREIGN KEY (`tournament_id`) REFERENCES `tournament` (`id`) ON DELETE CASCADE
) COMMENT='赛事分组';

CREATE TABLE `tournament_group_member` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tournament_id` BIGINT NOT NULL,
  `group_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `seed_no` INT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tgm_tournament_user` (`tournament_id`, `user_id`),
  KEY `idx_tgm_group` (`group_id`),
  CONSTRAINT `fk_tgm_tournament` FOREIGN KEY (`tournament_id`) REFERENCES `tournament` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_tgm_group` FOREIGN KEY (`group_id`) REFERENCES `tournament_group` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_tgm_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) COMMENT='分组名单';

ALTER TABLE `match`
  ADD COLUMN `phase_code` VARCHAR(32) NULL COMMENT 'GROUP/QUALIFIER/MAIN/FINAL' AFTER `category`,
  ADD COLUMN `group_id` BIGINT NULL AFTER `phase_code`,
  ADD COLUMN `home_user_id` BIGINT NULL AFTER `player2_id`,
  ADD COLUMN `away_user_id` BIGINT NULL AFTER `home_user_id`,
  ADD COLUMN `result_locked` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '管理员/主办验收后锁定' AFTER `status`,
  ADD COLUMN `accepted_by_user_id` BIGINT NULL AFTER `result_locked`,
  ADD COLUMN `accepted_at` DATETIME NULL AFTER `accepted_by_user_id`,
  ADD KEY `idx_match_group` (`group_id`),
  ADD CONSTRAINT `fk_match_group` FOREIGN KEY (`group_id`) REFERENCES `tournament_group` (`id`) ON DELETE SET NULL,
  ADD CONSTRAINT `fk_match_home` FOREIGN KEY (`home_user_id`) REFERENCES `user` (`id`) ON DELETE SET NULL,
  ADD CONSTRAINT `fk_match_away` FOREIGN KEY (`away_user_id`) REFERENCES `user` (`id`) ON DELETE SET NULL,
  ADD CONSTRAINT `fk_match_accepted_by` FOREIGN KEY (`accepted_by_user_id`) REFERENCES `user` (`id`) ON DELETE SET NULL;
