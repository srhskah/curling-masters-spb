-- 个人消息通知 RSS 订阅令牌（未登录可读公共源；登录用户凭 token 拉专属源）
SET @db := DATABASE();

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db
      AND TABLE_NAME = 'user'
      AND COLUMN_NAME = 'rss_token'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `user` ADD COLUMN `rss_token` VARCHAR(64) NULL COMMENT ''通知 RSS 专属订阅令牌'' AFTER `include_in_total_ranking`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @db
      AND TABLE_NAME = 'user'
      AND INDEX_NAME = 'uk_user_rss_token'
);
SET @sql_idx := IF(@idx_exists = 0,
    'CREATE UNIQUE INDEX `uk_user_rss_token` ON `user` (`rss_token`)',
    'SELECT 1');
PREPARE stmt_idx FROM @sql_idx;
EXECUTE stmt_idx;
DEALLOCATE PREPARE stmt_idx;
