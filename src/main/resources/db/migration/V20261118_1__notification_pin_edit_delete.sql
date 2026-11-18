SET time_zone = '+8:00';

-- 消息通知置顶
SET @sql := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'notification_message'
              AND COLUMN_NAME = 'pinned'
        ),
        'SELECT 1',
        'ALTER TABLE notification_message ADD COLUMN pinned TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否置顶'' AFTER publish_to_home'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'notification_message'
              AND INDEX_NAME = 'idx_notification_pinned'
        ),
        'SELECT 1',
        'ALTER TABLE notification_message ADD INDEX idx_notification_pinned (pinned, published, published_at)'
    )
);
PREPARE stmt FROM @idx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS notification_edit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    message_id BIGINT NOT NULL,
    editor_user_id BIGINT NULL,
    action VARCHAR(16) NOT NULL COMMENT 'CREATE/UPDATE',
    old_title VARCHAR(255) NULL,
    new_title VARCHAR(255) NULL,
    old_content_markdown LONGTEXT NULL,
    new_content_markdown LONGTEXT NULL,
    old_publish_to_home TINYINT(1) NULL,
    new_publish_to_home TINYINT(1) NULL,
    old_published TINYINT(1) NULL,
    new_published TINYINT(1) NULL,
    old_pinned TINYINT(1) NULL,
    new_pinned TINYINT(1) NULL,
    signature LONGTEXT NULL,
    edited_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_notif_edit_message (message_id, edited_at),
    INDEX idx_notif_edit_editor (editor_user_id),
    CONSTRAINT fk_notif_edit_message
        FOREIGN KEY (message_id) REFERENCES notification_message(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_notif_edit_editor
        FOREIGN KEY (editor_user_id) REFERENCES user(id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notification_delete_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    message_id BIGINT NULL COMMENT '原通知ID（删除后仅作追溯）',
    title VARCHAR(255) NOT NULL,
    content_markdown LONGTEXT NULL,
    content_html LONGTEXT NULL,
    author_user_id BIGINT NULL,
    source_type VARCHAR(32) NULL,
    source_ref_id BIGINT NULL,
    publish_to_home TINYINT(1) NULL,
    published TINYINT(1) NULL,
    pinned TINYINT(1) NULL,
    published_at DATETIME NULL,
    deleted_by_user_id BIGINT NULL,
    signature LONGTEXT NULL,
    deleted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_notif_delete_at (deleted_at),
    INDEX idx_notif_delete_by (deleted_by_user_id),
    CONSTRAINT fk_notif_delete_by
        FOREIGN KEY (deleted_by_user_id) REFERENCES user(id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
