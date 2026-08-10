package com.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户禁止报名记录
 */
@Getter
@Setter
@TableName("user_registration_ban")
public class UserRegistrationBan {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    /** 被禁止报名的用户名（查询时填充，不落库） */
    @TableField(exist = false)
    private String username;

    /** 禁止报名类型 */
    private String banType;

    /** 目标赛事等级代码 */
    @TableField("target_level_code")
    private String targetLevelCode;

    /** 目标赛季ID */
    @TableField("target_season_id")
    private Long targetSeasonId;

    /** 目标年份 */
    @TableField("target_year")
    private Integer targetYear;

    /** 禁止数量（届次/系列数） */
    private Integer banCount;

    /** 是否禁止赛季总决赛/年终总决赛 */
    @TableField("ban_final_tournament")
    private Boolean banFinalTournament;

    /** 禁止理由 */
    private String reason;

    /** 操作管理员ID */
    @TableField("created_by")
    private Long createdBy;

    /** 操作管理员用户名（查询时填充，不落库） */
    @TableField(exist = false)
    private String createdByUsername;

    /** 关联的消息通知ID */
    @TableField("notification_message_id")
    private Long notificationMessageId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}