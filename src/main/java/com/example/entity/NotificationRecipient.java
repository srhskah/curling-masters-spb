package com.example.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("notification_recipient")
public class NotificationRecipient {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("message_id")
    private Long messageId;

    @TableField("user_id")
    private Long userId;

    @TableField("read_at")
    private LocalDateTime readAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 重置密码状态：PENDING-待处理，RESET_BY_OTHER-已被其他管理员重置，RESET_BY_ME-由我重置 */
    @TableField("reset_password_status")
    private String resetPasswordStatus;

    /** 执行重置密码操作的管理员ID */
    @TableField("reset_password_by_user_id")
    private Long resetPasswordByUserId;

    /** 重置密码操作时间 */
    @TableField("reset_password_at")
    private LocalDateTime resetPasswordAt;

    /** 执行重置密码操作的管理员用户名（查询时填充，不落库） */
    @TableField(exist = false)
    private String resetPasswordByUsername;
}
