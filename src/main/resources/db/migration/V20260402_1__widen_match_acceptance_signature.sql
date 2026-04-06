-- 手写电子签名为 Base64 图片时远超 255 字符
ALTER TABLE `match_acceptance`
    MODIFY COLUMN `signature` MEDIUMTEXT NOT NULL COMMENT '电子签名（含 Base64 图片数据）';
