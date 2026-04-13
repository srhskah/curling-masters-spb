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
@TableName("notification_message")
public class NotificationMessage {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    @TableField("content_markdown")
    private String contentMarkdown;

    @TableField("content_html")
    private String contentHtml;

    private Boolean published;

    @TableField("publish_to_home")
    private Boolean publishToHome;

    @TableField("author_user_id")
    private Long authorUserId;

    /** 发起者用户名（查询时填充，不落库） */
    @TableField(exist = false)
    private String authorUsername;

    /** 关联赛事届次标题（赛季-赛事等级-届次），查询时填充，不落库 */
    @TableField(exist = false)
    private String tournamentEditionLabel;

    /** 关联赛事详情入口，查询时填充，不落库 */
    @TableField(exist = false)
    private String tournamentDetailUrl;

    @TableField("source_type")
    private String sourceType;

    @TableField("source_ref_id")
    private Long sourceRefId;

    @TableField("published_at")
    private LocalDateTime publishedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
