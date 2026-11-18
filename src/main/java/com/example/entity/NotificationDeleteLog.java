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
@TableName("notification_delete_log")
public class NotificationDeleteLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("message_id")
    private Long messageId;

    private String title;

    @TableField("content_markdown")
    private String contentMarkdown;

    @TableField("content_html")
    private String contentHtml;

    @TableField("author_user_id")
    private Long authorUserId;

    @TableField("source_type")
    private String sourceType;

    @TableField("source_ref_id")
    private Long sourceRefId;

    @TableField("publish_to_home")
    private Boolean publishToHome;

    private Boolean published;

    private Boolean pinned;

    @TableField("published_at")
    private LocalDateTime publishedAt;

    @TableField("deleted_by_user_id")
    private Long deletedByUserId;

    private String signature;

    @TableField("deleted_at")
    private LocalDateTime deletedAt;

    @TableField(exist = false)
    private String deletedByUsername;

    @TableField(exist = false)
    private String authorUsername;
}
