-- 取消资格确认为 Base64 图片时可能远超 VARCHAR 容量
ALTER TABLE `tournament_disqualification_acceptance`
    MODIFY COLUMN `signature` MEDIUMTEXT NOT NULL
    COMMENT '取消资格确认的电子签名（含 Base64 图片数据）';

