-- 小组赛禁平局时的加局积分模式 + 赛事等级默认值（仅影响之后新建/首次保存配置的赛事）
SET time_zone = '+8:00';
SET @db := DATABASE();

-- tournament_competition_config.group_ot_points_mode
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'tournament_competition_config'
      AND COLUMN_NAME = 'group_ot_points_mode'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `tournament_competition_config` ADD COLUMN `group_ot_points_mode` VARCHAR(8) NULL DEFAULT ''2_1'' COMMENT ''禁平局加局积分：2_1=胜+2败+1，3_0=胜+3败+0'' AFTER `group_allow_draw`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- tournament_level.group_allow_draw
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'tournament_level'
      AND COLUMN_NAME = 'group_allow_draw'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `tournament_level` ADD COLUMN `group_allow_draw` TINYINT(1) NULL DEFAULT 1 COMMENT ''新建赛事默认：小组赛是否允许平局'' AFTER `description`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- tournament_level.group_ot_points_mode
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'tournament_level'
      AND COLUMN_NAME = 'group_ot_points_mode'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `tournament_level` ADD COLUMN `group_ot_points_mode` VARCHAR(8) NULL DEFAULT ''2_1'' COMMENT ''新建赛事默认：禁平局加局积分 2_1 / 3_0'' AFTER `group_allow_draw`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
