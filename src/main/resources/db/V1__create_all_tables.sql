-- 设置数据库时区
SET time_zone = '+8:00';

CREATE TABLE `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '登录名',
  `password` VARCHAR(255) NOT NULL COMMENT 'BCrypt加密',
  `avatar` VARCHAR(255) DEFAULT '/default/avatar.png' COMMENT '头像路径',
  `email` VARCHAR(100) DEFAULT NULL,
  `role` TINYINT NOT NULL DEFAULT 2 COMMENT '0-超级管理员,1-普通管理员,2-普通用户',
  `password_changed` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否已修改初始密码(123456)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_role` (`role`)
);

CREATE TABLE `season` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `year` INT NOT NULL,
  `half` TINYINT NOT NULL COMMENT '1-上半年,2-下半年',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_season` (`year`, `half`)
);

CREATE TABLE `series` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `season_id` BIGINT NOT NULL,
  `sequence` INT NOT NULL COMMENT '该赛季内的序号',
  `name` VARCHAR(100) DEFAULT NULL COMMENT '系列名称',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_series` (`season_id`, `sequence`),
  FOREIGN KEY (`season_id`) REFERENCES `season`(`id`) ON DELETE CASCADE
);

CREATE TABLE `tournament_level` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(50) NOT NULL UNIQUE COMMENT '等级代码，如年终总决赛、2000赛等',
  `name` VARCHAR(100) NOT NULL,
  `default_champion_ratio` DECIMAL(5,2) NOT NULL COMMENT '默认冠军积分比率(%)',
  `default_bottom_points` INT NOT NULL COMMENT '垫底积分',
  `description` VARCHAR(255),
  PRIMARY KEY (`id`)
);

CREATE TABLE `tournament` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `series_id` BIGINT NOT NULL,
  `level_code` VARCHAR(50) NOT NULL COMMENT '关联tournament_level.code',
  `host_user_id` BIGINT NOT NULL COMMENT '主办用户',
  `champion_points_ratio` DECIMAL(5,2) NOT NULL COMMENT '实际使用的冠军积分比率（可修改）',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0-筹备中,1-进行中,2-已结束',
  `start_date` DATE DEFAULT NULL,
  `end_date` DATE DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  FOREIGN KEY (`series_id`) REFERENCES `series`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`host_user_id`) REFERENCES `user`(`id`),
  FOREIGN KEY (`level_code`) REFERENCES `tournament_level`(`code`)
);

CREATE TABLE `tournament_entry` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tournament_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `entry_type` TINYINT NOT NULL COMMENT '1-直接入选,2-资格赛晋级,3-替补',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_entry` (`tournament_id`, `user_id`),
  FOREIGN KEY (`tournament_id`) REFERENCES `tournament`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
);

CREATE TABLE `match` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tournament_id` BIGINT NOT NULL,
  `category` VARCHAR(50) NOT NULL COMMENT '类别：1000赛资格赛、1/8决赛等',
  `round` INT NOT NULL COMMENT '轮次，用于排序',
  `player1_id` BIGINT DEFAULT NULL,
  `player2_id` BIGINT DEFAULT NULL,
  `winner_id` BIGINT DEFAULT NULL COMMENT '冗余胜者，可通过比分计算',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0-未开始,1-进行中,2-已结束,3-退赛',
  `scheduled_time` DATETIME DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  FOREIGN KEY (`tournament_id`) REFERENCES `tournament`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`player1_id`) REFERENCES `user`(`id`),
  FOREIGN KEY (`player2_id`) REFERENCES `user`(`id`),
  FOREIGN KEY (`winner_id`) REFERENCES `user`(`id`),
  INDEX `idx_tournament` (`tournament_id`)
);

CREATE TABLE `set_score` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `match_id` BIGINT NOT NULL,
  `set_number` INT NOT NULL COMMENT '第几局',
  `player1_score` INT NOT NULL DEFAULT 0,
  `player2_score` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_set` (`match_id`, `set_number`),
  FOREIGN KEY (`match_id`) REFERENCES `match`(`id`) ON DELETE CASCADE
);

CREATE TABLE `user_tournament_points` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `tournament_id` BIGINT NOT NULL,
  `points` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_user_tournament` (`user_id`, `tournament_id`),
  FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`tournament_id`) REFERENCES `tournament`(`id`) ON DELETE CASCADE,
  INDEX `idx_user_points` (`user_id`, `points`)
);

CREATE TABLE `notification` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `type` TINYINT NOT NULL COMMENT '1-no-operation,2-超管申请,3-普管申请,4-退赛通知等',
  `receiver_user_id` BIGINT DEFAULT NULL COMMENT '指定用户，为空则按角色',
  `receiver_role` TINYINT DEFAULT NULL COMMENT '接收角色：0-超管,1-普管,2-普用',
  `content` TEXT NOT NULL,
  `related_id` BIGINT DEFAULT NULL COMMENT '关联业务ID（如退赛申请ID）',
  `is_read` BOOLEAN NOT NULL DEFAULT FALSE,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  FOREIGN KEY (`receiver_user_id`) REFERENCES `user`(`id`) ON DELETE SET NULL,
  INDEX `idx_receiver` (`receiver_user_id`, `is_read`)
);

CREATE TABLE `withdrawal_request` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `tournament_id` BIGINT NOT NULL,
  `reason` TEXT,
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0-待审核,1-已通过,2-已拒绝',
  `processed_by` BIGINT DEFAULT NULL COMMENT '审核管理员ID',
  `processed_at` DATETIME DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`tournament_id`) REFERENCES `tournament`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`processed_by`) REFERENCES `user`(`id`)
);

CREATE TABLE `admin_ip_whitelist` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `ip_address` VARCHAR(45) NOT NULL UNIQUE,
  `description` VARCHAR(255),
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);