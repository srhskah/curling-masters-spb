package com.example.service;

import com.example.entity.NotificationMessage;
import com.example.entity.NotificationRecipient;
import com.example.entity.User;

import java.util.List;
import java.util.Optional;

public interface INotificationService {
    NotificationMessage createOrUpdate(Long id, String title, String markdown, boolean publishToHome, boolean publishNow, User operator);
    List<NotificationMessage> listInbox(Long userId, int limit);
    List<NotificationMessage> listManage(int limit);
    List<NotificationMessage> listPublishedForHome(int limit);

    /**
     * 未登录可见的公共已发布通知（与首页同源：published + publishToHome）。
     */
    List<NotificationMessage> listPublicPublished(int limit);

    /**
     * 按用户权限与当前已报名/主办赛事可见的通知列表（匿名时等同公共列表）。
     */
    List<NotificationMessage> listReadableForUser(User viewer, int limit);

    /**
     * 是否可阅读该已发布通知（匿名仅公共；登录按权限/报名/收件范围）。
     */
    boolean canRead(NotificationMessage message, User viewer);

    Optional<NotificationMessage> getReadableDetail(Long messageId, User viewer);
    Optional<NotificationMessage> getManageDetail(Long messageId, User operator);
    void markRead(Long messageId, Long userId);
    void markUnread(Long messageId, Long userId);
    /** 将当前用户所有未读收件记录标为已读，返回更新条数 */
    int markAllRead(Long userId);
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

    /**
     * 向单个用户发送一条内容可因人而异的通知。同一 sourceType + sourceRefId + 用户只发一次。
     */
    void sendPersonalNotification(String title, String markdown, String sourceType, Long sourceRefId, Long userId);

    /**
     * 发送忘记密码请求通知给所有管理员
     */
    void sendForgotPasswordNotification(Long requesterUserId);

    /**
     * 管理员重置用户密码
     */
    void resetUserPassword(Long messageId, Long adminUserId, Long targetUserId);

    /**
     * 获取消息通知的重置密码相关 recipient 信息
     */
    List<NotificationRecipient> getResetPasswordRecipients(Long messageId);

    /**
     * 获取当前用户在指定消息中的重置密码状态
     */
    Optional<NotificationRecipient> getResetPasswordRecipientStatus(Long messageId, Long userId);
}
