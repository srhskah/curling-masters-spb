SET time_zone = '+8:00';

ALTER TABLE `tournament_competition_config`
  ADD COLUMN `knockout_start_round` INT NULL COMMENT '淘汰赛首轮：16/8/4/2(半决赛)' AFTER `match_mode`,
  ADD COLUMN `qualifier_round` INT NULL COMMENT '资格赛挂载的淘汰轮次（同赛事仅允许一轮）' AFTER `knockout_start_round`;
