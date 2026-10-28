-- 小组：唯一 ≥3 人同分且胜平负相同时，可选用完整小组数据而非伪小组重排

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'tournament_group'
    AND COLUMN_NAME = 'rank_tie_mode'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `tournament_group` ADD COLUMN `rank_tie_mode` VARCHAR(16) NOT NULL DEFAULT ''PSEUDO'' COMMENT ''PSEUDO=拆伪小组；GROUP=用小组数据排同分块'' AFTER `group_order`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
