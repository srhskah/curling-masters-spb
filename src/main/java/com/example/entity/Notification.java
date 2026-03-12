package com.example.entity;

import java.time.LocalDateTime;

public class Notification {
    private Long id;
    private Integer type; // 1-no-operation,2-超管申请,3-普管申请,4-退赛通知等
    private Long receiverUserId; // 指定用户，为空则按角色
    private Integer receiverRole; // 接收角色：0-超管,1-普管,2-普用
    private String content;
    private Long relatedId; // 关联业务ID（如退赛申请ID）
    private Boolean isRead;
    private LocalDateTime createdAt;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public Long getReceiverUserId() {
        return receiverUserId;
    }

    public void setReceiverUserId(Long receiverUserId) {
        this.receiverUserId = receiverUserId;
    }

    public Integer getReceiverRole() {
        return receiverRole;
    }

    public void setReceiverRole(Integer receiverRole) {
        this.receiverRole = receiverRole;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getRelatedId() {
        return relatedId;
    }

    public void setRelatedId(Long relatedId) {
        this.relatedId = relatedId;
    }

    public Boolean getIsRead() {
        return isRead;
    }

    public void setIsRead(Boolean isRead) {
        this.isRead = isRead;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
