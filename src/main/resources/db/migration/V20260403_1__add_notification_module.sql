CREATE TABLE IF NOT EXISTS notification_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    content_markdown LONGTEXT NOT NULL,
    content_html LONGTEXT NULL,
    published TINYINT(1) NOT NULL DEFAULT 0,
    publish_to_home TINYINT(1) NOT NULL DEFAULT 0,
    author_user_id BIGINT NULL,
    source_type VARCHAR(32) NULL,
    source_ref_id BIGINT NULL,
    published_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_notification_publish (published, publish_to_home, published_at),
    INDEX idx_notification_author (author_user_id),
    INDEX idx_notification_source (source_type, source_ref_id),
    CONSTRAINT fk_notification_author_user
        FOREIGN KEY (author_user_id) REFERENCES user(id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notification_recipient (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    message_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    read_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_notification_message_user (message_id, user_id),
    INDEX idx_notification_user_read (user_id, read_at),
    INDEX idx_notification_message (message_id),
    CONSTRAINT fk_notification_recipient_message
        FOREIGN KEY (message_id) REFERENCES notification_message(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_notification_recipient_user
        FOREIGN KEY (user_id) REFERENCES user(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
