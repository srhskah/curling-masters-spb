-- 用户是否计入总排名（默认计入）
ALTER TABLE `user`
  ADD COLUMN `include_in_total_ranking` BOOLEAN NOT NULL DEFAULT TRUE
  COMMENT '是否计入总排名' AFTER `password_changed`;

