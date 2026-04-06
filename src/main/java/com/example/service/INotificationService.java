package com.example.service;

import com.example.entity.NotificationMessage;
import com.example.entity.User;

import java.util.List;
import java.util.Optional;

public interface INotificationService {
    NotificationMessage createOrUpdate(Long id, String title, String markdown, boolean publishToHome, boolean publishNow, User operator);
    List<NotificationMessage> listInbox(Long userId, int limit);
    List<NotificationMessage> listManage(int limit);
    List<NotificationMessage> listPublishedForHome(int limit);
    Optional<NotificationMessage> getReadableDetail(Long messageId, Long viewerUserId);
    Optional<NotificationMessage> getManageDetail(Long messageId, User operator);
    void markRead(Long messageId, Long userId);
    void markUnread(Long messageId, Long userId);
    long unreadCount(Long userId);
    boolean canManage(User user);
    /** 是否可修改该条通知（创建者本人；历史数据 author 为空时仅超级管理员可改） */
    boolean canEditNotification(User operator, NotificationMessage message);
    String buildCopyText(NotificationMessage message);
    void sendSystemNotification(String title, String markdown, String sourceType, Long sourceRefId);

    /**
     * 仅向指定用户发送站内通知（用于赛事抽签等定向场景，不广播全站）。
     */
    void sendNotificationToUserIds(String title, String markdown, String sourceType, Long sourceRefId,
                                   java.util.Collection<Long> recipientUserIds);
}
