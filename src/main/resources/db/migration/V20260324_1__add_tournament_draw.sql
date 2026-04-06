-- 抽签配置表
CREATE TABLE IF NOT EXISTS tournament_draw (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tournament_id BIGINT NOT NULL COMMENT '赛事ID',
    draw_type VARCHAR(20) NOT NULL COMMENT '抽签类型：TIERED(分档抽签), RANDOM(随机抽签)',
    group_count INT NOT NULL COMMENT '小组数',
    tier_count INT DEFAULT NULL COMMENT '档数（仅分档抽签）',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING(待抽签), IN_PROGRESS(抽签中), COMPLETED(已完成)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tournament (tournament_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='赛事抽签配置';

-- 抽签结果表
CREATE TABLE IF NOT EXISTS tournament_draw_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tournament_id BIGINT NOT NULL COMMENT '赛事ID',
    user_id BIGINT NOT NULL COMMENT '选手ID',
    group_id BIGINT NOT NULL COMMENT '小组ID',
    tier_number INT DEFAULT NULL COMMENT '档位（仅分档抽签）',
    draw_order INT NOT NULL COMMENT '抽签顺序',
    is_auto_assigned BOOLEAN DEFAULT FALSE COMMENT '是否自动分配',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tournament_user (tournament_id, user_id),
    KEY idx_tournament_group (tournament_id, group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='赛事抽签结果';
