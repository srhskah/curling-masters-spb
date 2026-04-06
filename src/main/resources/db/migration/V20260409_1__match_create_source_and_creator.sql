-- 记录淘汰赛场次创建来源与操作者，便于区分自动/手动排签
SET @db := DATABASE();

SET @col_creator_exists := (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db
      AND TABLE_NAME = 'match'
      AND COLUMN_NAME = 'created_by_user_id'
);
SET @sql_creator := IF(
    @col_creator_exists = 0,
    'ALTER TABLE `match` ADD COLUMN `created_by_user_id` BIGINT NULL COMMENT ''场次创建者用户ID'' AFTER `accepted_at`',
    'SELECT 1'
);
PREPARE stmt_creator FROM @sql_creator;
EXECUTE stmt_creator;
DEALLOCATE PREPARE stmt_creator;

SET @col_source_exists := (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db
      AND TABLE_NAME = 'match'
      AND COLUMN_NAME = 'create_source'
);
SET @sql_source := IF(
    @col_source_exists = 0,
    'ALTER TABLE `match` ADD COLUMN `create_source` VARCHAR(64) NULL COMMENT ''场次创建来源'' AFTER `created_by_user_id`',
    'SELECT 1'
);
PREPARE stmt_source FROM @sql_source;
EXECUTE stmt_source;
DEALLOCATE PREPARE stmt_source;
