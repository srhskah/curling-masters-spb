-- 淘汰赛首轮抽签：配置开关，以及抽签结果按分池区分选手（避免与小组抽签互相覆盖）
SET time_zone = '+8:00';

ALTER TABLE tournament_competition_config
    ADD COLUMN knockout_first_draw_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用淘汰赛首轮抽签' AFTER knockout_auto_from_group,
    ADD COLUMN knockout_qualifier_draw TINYINT NOT NULL DEFAULT 0 COMMENT '0资格赛保持原对阵 1资格赛参与抽签' AFTER knockout_first_draw_enabled;

ALTER TABLE tournament_draw_result DROP INDEX uk_tournament_user;

ALTER TABLE tournament_draw_result
    ADD UNIQUE KEY uk_tournament_pool_user (tournament_id, draw_pool, user_id);
