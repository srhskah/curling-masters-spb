-- 用户禁止报名表
CREATE TABLE IF NOT EXISTS user_registration_ban (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '被禁止报名的用户ID',
    ban_type VARCHAR(32) NOT NULL COMMENT '禁止报名类型：FUTURE_TOURNAMENTS_BY_LEVEL-往后N届特定等级赛事，FUTURE_SERIES_ALL-往后N个系列所有赛事，SEASON_ALL_BY_LEVEL-本赛季特定等级所有赛事，SEASON_ALL-本赛季所有赛事，YEAR_ALL-本年度所有赛事',
    target_level_code VARCHAR(32) NULL COMMENT '目标赛事等级代码（针对特定等级的禁止类型）',
    target_season_id BIGINT NULL COMMENT '目标赛季ID（针对赛季相关禁止类型）',
    target_year INT NULL COMMENT '目标年份（针对年度相关禁止类型）',
    ban_count INT NULL COMMENT '禁止数量（届次/系列数）',
    ban_final_tournament TINYINT(1) DEFAULT 0 COMMENT '是否禁止赛季总决赛/年终总决赛',
    reason TEXT NOT NULL COMMENT '禁止理由',
    created_by BIGINT NOT NULL COMMENT '操作管理员ID',
    notification_message_id BIGINT NULL COMMENT '关联的消息通知ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_ban_type (ban_type),
    INDEX idx_target_level (target_level_code),
    INDEX idx_target_season (target_season_id),
    INDEX idx_target_year (target_year),
    CONSTRAINT fk_user_registration_ban_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_registration_ban_admin FOREIGN KEY (created_by) REFERENCES user(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_registration_ban_notification FOREIGN KEY (notification_message_id) REFERENCES notification_message(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户禁止报名记录表';