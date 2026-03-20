-- 检查字段是否已存在，避免重复执行
SET @dbname = DATABASE();
SET @tablename = 'match';
SET @columnname = 'first_end_hammer';

SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
  "SELECT 'Column already exists'",
  "ALTER TABLE `match` ADD COLUMN `first_end_hammer` TINYINT DEFAULT NULL COMMENT '第一局后手方'"
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 继续添加其他字段...
