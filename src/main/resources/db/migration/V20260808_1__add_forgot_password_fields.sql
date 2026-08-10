-- 添加忘记密码功能相关字段
-- 为 notification_recipient 添加重置密码操作状态字段
ALTER TABLE notification_recipient 
ADD COLUMN reset_password_status VARCHAR(32) NULL COMMENT '重置密码状态：PENDING-待处理，RESET_BY_OTHER-已被其他管理员重置，RESET_BY_ME-由我重置',
ADD COLUMN reset_password_by_user_id BIGINT NULL COMMENT '执行重置密码操作的管理员ID',
ADD COLUMN reset_password_at DATETIME NULL COMMENT '重置密码操作时间',
ADD INDEX idx_recipient_reset_status (message_id, reset_password_status);