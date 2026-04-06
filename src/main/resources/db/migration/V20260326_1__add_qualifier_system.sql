-- 资格赛系统数据库升级脚本
-- V20260326_1: 添加资格赛系统支持

-- 1. 修改 tournament_group 表，添加组类型字段
ALTER TABLE tournament_group 
ADD COLUMN group_type VARCHAR(20) DEFAULT 'GROUP' COMMENT '组类型：GROUP=小组赛，QUALIFIER=资格赛种子位';

-- 2. 修改 tournament_group_member 表，添加资格赛相关字段
ALTER TABLE tournament_group_member 
ADD COLUMN is_eliminated TINYINT(1) DEFAULT 0 COMMENT '是否已淘汰（资格赛用）',
ADD COLUMN tier INT NULL COMMENT '档次（资格赛用）',
ADD COLUMN registration_order INT NULL COMMENT '报名顺序（资格赛用）';

-- 3. 修改 match 表，添加资格赛轮次字段
ALTER TABLE `match` 
ADD COLUMN qualifier_round INT NULL COMMENT '资格赛轮次（NULL表示非资格赛）';

-- 4. 修改 tournament_competition_config 表，添加资格赛局数配置
ALTER TABLE tournament_competition_config 
ADD COLUMN qualifier_sets INT DEFAULT 8 COMMENT '资格赛局数（默认8局）';
