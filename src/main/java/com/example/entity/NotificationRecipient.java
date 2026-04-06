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
}
