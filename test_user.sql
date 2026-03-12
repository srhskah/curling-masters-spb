-- 插入测试用户 (密码都是: 123456)
INSERT INTO user (username, password, email, role, password_changed, created_at, updated_at)
VALUES
('testadmin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lbdxpTK0T8JZ9rHbO', 'admin@example.com', 0, true, NOW(), NOW()),
('testuser', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lbdxpTK0T8JZ9rHbO', 'user@example.com', 2, true, NOW(), NOW());
