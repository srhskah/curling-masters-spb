SET time_zone = '+8:00';

ALTER TABLE tournament_competition_config
    ADD COLUMN knockout_bracket_mode TINYINT NULL DEFAULT 0 AFTER knockout_start_round,
    ADD COLUMN knockout_auto_from_group TINYINT NOT NULL DEFAULT 1 AFTER knockout_bracket_mode;

ALTER TABLE `match`
    ADD COLUMN knockout_bracket_slot INT NULL AFTER qualifier_round,
    ADD COLUMN feeder_match1_id BIGINT NULL AFTER knockout_bracket_slot,
    ADD COLUMN feeder_match2_id BIGINT NULL AFTER feeder_match1_id,
    ADD COLUMN knockout_half TINYINT NULL AFTER feeder_match2_id;
