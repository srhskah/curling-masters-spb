-- 首轮淘汰赛附加资格赛独立截止时间（与正赛首轮截止分开）
SET @db := DATABASE();

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db
    AND TABLE_NAME = 'tournament_competition_config'
    AND COLUMN_NAME = 'knockout_qualifier_deadline'
);
SET @sql := IF(
  @exists = 0,
  'ALTER TABLE `tournament_competition_config` ADD COLUMN `knockout_qualifier_deadline` DATETIME NULL COMMENT ''首轮淘汰赛附加资格赛截止时间'' AFTER `knockout_round_1_deadline`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
