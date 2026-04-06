-- 抽签扩展：种子人数、开放时刻；结果表位次与种子标记
ALTER TABLE tournament_draw
    ADD COLUMN seed_count INT NULL COMMENT '种子抽签：种子人数（须为小组数的正整数倍）' AFTER tier_count,
    ADD COLUMN draw_opened_at TIMESTAMP NULL COMMENT '抽签开放（报名截止后或手动触发）' AFTER updated_at;

ALTER TABLE tournament_draw_result
    ADD COLUMN is_seed TINYINT(1) NULL DEFAULT NULL COMMENT '种子抽签：是否种子选手' AFTER tier_number,
    ADD COLUMN group_slot_index INT NULL COMMENT '组内位次 1..每组人数' AFTER is_seed;
