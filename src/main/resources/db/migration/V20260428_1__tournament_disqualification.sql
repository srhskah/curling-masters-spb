SET time_zone = '+8:00';

CREATE TABLE IF NOT EXISTS `tournament_disqualification` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tournament_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `reason` VARCHAR(2000) NOT NULL,
  `effective` TINYINT(1) NOT NULL DEFAULT 0,
  `effective_at` DATETIME NULL,
  `created_by_user_id` BIGINT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tournament_user` (`tournament_id`, `user_id`),
  KEY `idx_tournament_effective` (`tournament_id`, `effective`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='赛事取消资格（小组赛）';

CREATE TABLE IF NOT EXISTS `tournament_disqualification_acceptance` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `dq_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `signature` VARCHAR(2000) NOT NULL,
  `accepted_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dq_user` (`dq_id`, `user_id`),
  KEY `idx_dq` (`dq_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='取消资格确认（办赛人员电子签名，需>=2）';

