-- 创建系统配置表
CREATE TABLE IF NOT EXISTS `system_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `config_key` varchar(100) NOT NULL COMMENT '配置键',
  `config_value` varchar(500) DEFAULT NULL COMMENT '配置值',
  `description` varchar(255) DEFAULT NULL COMMENT '配置描述',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 插入赛制规则默认值配置
INSERT IGNORE INTO `system_config` (`config_key`, `config_value`, `description`) VALUES
('competition_rule_default_center_line_limit', '5', '默认不允许磕中线壶的数量'),
('competition_rule_default_dead_stone_limit', '5', '默认不允许打成死壶的数量');

-- 为tournament表添加赛制规则字段
-- ALTER TABLE `tournament` 
-- ADD COLUMN `competition_rule_type` varchar(20) DEFAULT 'NORMAL' COMMENT '赛制规则类型：NORMAL-正常5壶保护制，CUSTOM-自定义',
-- ADD COLUMN `custom_center_line_limit` int DEFAULT NULL COMMENT '自定义不允许磕中线壶的数量（0-16）',
-- ADD COLUMN `custom_dead_stone_limit` int DEFAULT NULL COMMENT '自定义不允许打成死壶的数量（0-16）';