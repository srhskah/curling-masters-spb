-- 各阶段淘汰赛截止时间 + 「上一阶段全部结束才可进入下一阶段」开关（默认 OFF）
SET time_zone = '+8:00';
SET @db := DATABASE();

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'tournament_competition_config'
      AND COLUMN_NAME = 'knockout_round_16_deadline'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `tournament_competition_config` ADD COLUMN `knockout_round_16_deadline` DATETIME NULL COMMENT ''1/16决赛截止时间'' AFTER `group_stage_deadline`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'tournament_competition_config'
      AND COLUMN_NAME = 'knockout_round_8_deadline'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `tournament_competition_config` ADD COLUMN `knockout_round_8_deadline` DATETIME NULL COMMENT ''1/8决赛截止时间'' AFTER `knockout_round_16_deadline`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'tournament_competition_config'
      AND COLUMN_NAME = 'knockout_round_4_deadline'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `tournament_competition_config` ADD COLUMN `knockout_round_4_deadline` DATETIME NULL COMMENT ''1/4决赛截止时间'' AFTER `knockout_round_8_deadline`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'tournament_competition_config'
      AND COLUMN_NAME = 'knockout_round_2_deadline'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `tournament_competition_config` ADD COLUMN `knockout_round_2_deadline` DATETIME NULL COMMENT ''半决赛截止时间'' AFTER `knockout_round_4_deadline`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'tournament_competition_config'
      AND COLUMN_NAME = 'knockout_round_1_deadline'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `tournament_competition_config` ADD COLUMN `knockout_round_1_deadline` DATETIME NULL COMMENT ''决赛（金/铜牌赛）截止时间'' AFTER `knockout_round_2_deadline`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'tournament_competition_config'
      AND COLUMN_NAME = 'knockout_require_prev_stage_done'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `tournament_competition_config` ADD COLUMN `knockout_require_prev_stage_done` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''上一阶段淘汰赛全部结束后才可进入下一阶段（0关闭 1开启）'' AFTER `knockout_qualifier_draw`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT IGNORE INTO `system_config` (`config_key`, `config_value`, `description`)
VALUES ('knockout_require_prev_stage_done_default', 'false', '新建/未单独设置时：是否要求上一阶段淘汰赛全部结束后才可进入下一阶段（默认 OFF）');
