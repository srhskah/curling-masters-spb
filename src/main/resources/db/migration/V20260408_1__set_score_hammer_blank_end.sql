SET time_zone = '+8:00';

SET @db := DATABASE();

SET @exist := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'set_score' AND COLUMN_NAME = 'hammer_player_id'
);
SET @sql := IF(@exist = 0,
    'ALTER TABLE `set_score` ADD COLUMN hammer_player_id BIGINT NULL COMMENT ''该局持锤选手 user id'' AFTER created_at',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exist := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'set_score' AND COLUMN_NAME = 'is_blank_end'
);
SET @sql := IF(@exist = 0,
    'ALTER TABLE `set_score` ADD COLUMN is_blank_end TINYINT NOT NULL DEFAULT 0 COMMENT ''是否空局'' AFTER hammer_player_id',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
