-- 添加晋级淘汰赛人数字段
ALTER TABLE tournament_competition_config ADD COLUMN knockout_qualify_count INT DEFAULT NULL COMMENT '晋级淘汰赛人数';
