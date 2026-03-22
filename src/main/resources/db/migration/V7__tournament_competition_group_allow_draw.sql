SET time_zone = '+8:00';

ALTER TABLE `tournament_competition_config`
  ADD COLUMN `group_allow_draw` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '小组赛是否允许平局：1允许，0不允许' AFTER `group_size`;
