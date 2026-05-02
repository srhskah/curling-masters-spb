-- 报名禁报：可同时参照同系列多个赛事的前 K 名（逗号分隔赛事 ID）
SET time_zone = '+8:00';

ALTER TABLE `tournament_registration_setting`
  ADD COLUMN `ban_other_tournament_ids` VARCHAR(2048) NULL COMMENT '禁报参照的多个其他赛事ID，逗号分隔；非空时优先于单列 ban_other_tournament_id' AFTER `ban_other_tournament_id`;
