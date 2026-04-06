SET time_zone = '+8:00';

-- 列已存在时跳过（远端可能已手工加列或失败重试后列已存在）
SET @db := DATABASE();
SET @exist := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'match' AND COLUMN_NAME = 'first_end_hammer'
);
SET @sql := IF(@exist = 0,
    'ALTER TABLE `match` ADD COLUMN first_end_hammer TINYINT NULL COMMENT ''首局持锤方 1=player1 2=player2'' AFTER knockout_half',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
