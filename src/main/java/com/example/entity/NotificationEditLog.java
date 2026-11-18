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
@TableName("notification_edit_log")
public class NotificationEditLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("message_id")
    private Long messageId;

    @TableField("editor_user_id")
    private Long editorUserId;

    /** CREATE / UPDATE */
    private String action;

    @TableField("old_title")
    private String oldTitle;

    @TableField("new_title")
    private String newTitle;

    @TableField("old_content_markdown")
    private String oldContentMarkdown;

    @TableField("new_content_markdown")
    private String newContentMarkdown;

    @TableField("old_publish_to_home")
    private Boolean oldPublishToHome;

    @TableField("new_publish_to_home")
    private Boolean newPublishToHome;

    @TableField("old_published")
    private Boolean oldPublished;

    @TableField("new_published")
    private Boolean newPublished;

    @TableField("old_pinned")
    private Boolean oldPinned;

    @TableField("new_pinned")
    private Boolean newPinned;

    private String signature;

    @TableField("edited_at")
    private LocalDateTime editedAt;

    @TableField(exist = false)
    private String editorUsername;
}
