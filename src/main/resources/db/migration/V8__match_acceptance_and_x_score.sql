SET time_zone = '+8:00';

ALTER TABLE `tournament_competition_config`
  ADD COLUMN `group_stage_deadline` DATETIME NULL COMMENT '小组赛截止时间' AFTER `group_allow_draw`;

ALTER TABLE `set_score`
  ADD COLUMN `player1_is_x` TINYINT(1) NOT NULL DEFAULT 0 AFTER `player1_score`,
  ADD COLUMN `player2_is_x` TINYINT(1) NOT NULL DEFAULT 0 AFTER `player2_score`;

CREATE TABLE IF NOT EXISTS `match_acceptance` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `match_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `signature` VARCHAR(255) NOT NULL,
  `accepted_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_match_user` (`match_id`, `user_id`),
  KEY `idx_match_id` (`match_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='比赛验收记录（含电子签名）';

CREATE TABLE IF NOT EXISTS `match_score_edit_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `match_id` BIGINT NOT NULL,
  `set_number` INT NOT NULL,
  `editor_user_id` BIGINT NOT NULL,
  `old_player1_score` INT NULL,
  `old_player2_score` INT NULL,
  `new_player1_score` INT NULL,
  `new_player2_score` INT NULL,
  `old_player1_is_x` TINYINT(1) NOT NULL DEFAULT 0,
  `old_player2_is_x` TINYINT(1) NOT NULL DEFAULT 0,
  `new_player1_is_x` TINYINT(1) NOT NULL DEFAULT 0,
  `new_player2_is_x` TINYINT(1) NOT NULL DEFAULT 0,
  `edited_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_match_edited_at` (`match_id`, `edited_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='比分修改记录';
