-- 抽签分池：直通车 MAIN / 资格赛晋级 QUALIFIER；小组成员位次与资格赛抽签导入对齐
SET time_zone = '+8:00';

ALTER TABLE tournament_draw
    ADD COLUMN draw_pool VARCHAR(16) NOT NULL DEFAULT 'MAIN' COMMENT 'MAIN=直通车 QUALIFIER=资格赛晋级' AFTER tournament_id;

ALTER TABLE tournament_draw DROP INDEX uk_tournament;

ALTER TABLE tournament_draw ADD UNIQUE KEY uk_tournament_pool (tournament_id, draw_pool);

ALTER TABLE tournament_draw_result
    ADD COLUMN draw_pool VARCHAR(16) NOT NULL DEFAULT 'MAIN' COMMENT 'MAIN/QUALIFIER' AFTER tournament_id;

ALTER TABLE tournament_group_member
    ADD COLUMN slot_index INT NULL COMMENT '组内位次1..每组人数' AFTER seed_no;
