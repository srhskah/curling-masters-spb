-- 允许在同一赛事内录入多个“退赛”占位：
-- - user_id 允许为 NULL（NULL 不参与唯一约束冲突）
-- - 去掉 user_id 外键（否则无法插入 NULL）
-- - 保留 uniq_user_tournament(user_id, tournament_id) 用于约束真实用户

ALTER TABLE user_tournament_points
  DROP FOREIGN KEY user_tournament_points_ibfk_1;

ALTER TABLE user_tournament_points
  MODIFY COLUMN user_id BIGINT NULL;

