package com.example.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.entity.NotificationDeleteLog;
import com.example.entity.NotificationEditLog;
import com.example.entity.NotificationMessage;
import com.example.entity.NotificationRecipient;
import com.example.entity.Season;
import com.example.entity.Series;
import com.example.entity.Tournament;
import com.example.entity.TournamentGroup;
import com.example.entity.TournamentLevel;
import com.example.entity.TournamentRegistration;
import com.example.entity.User;
import com.example.mapper.NotificationDeleteLogMapper;
import com.example.mapper.NotificationEditLogMapper;
import com.example.mapper.NotificationMessageMapper;
import com.example.mapper.NotificationRecipientMapper;
import com.example.mapper.TournamentGroupMapper;
import com.example.mapper.TournamentRegistrationMapper;
import com.example.service.TournamentUrlService;
import com.example.service.INotificationService;
import com.example.service.ITournamentLevelService;
import com.example.service.SeasonService;
import com.example.service.SeriesService;
import com.example.service.TournamentService;
import com.example.service.UserService;
import com.example.util.MarkdownUtils;
import com.example.util.SignatureValidation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class NotificationMessageServiceImpl implements INotificationService {
    private static final int ROLE_SUPER_ADMIN = 0;
    private static final int ROLE_ADMIN = 1;

    private static final DateTimeFormatter COPY_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final NotificationMessageMapper messageMapper;
    private final NotificationRecipientMapper recipientMapper;
    private final NotificationEditLogMapper editLogMapper;
    private final NotificationDeleteLogMapper deleteLogMapper;
    private final TournamentRegistrationMapper registrationMapper;
    private final UserService userService;
    private final TournamentService tournamentService;
    private final SeriesService seriesService;
    private final SeasonService seasonService;
    private final ITournamentLevelService tournamentLevelService;
    private final TournamentUrlService tournamentUrlService;
    private final TournamentGroupMapper tournamentGroupMapper;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public NotificationMessageServiceImpl(NotificationMessageMapper messageMapper,
                                          NotificationRecipientMapper recipientMapper,
                                          NotificationEditLogMapper editLogMapper,
                                          NotificationDeleteLogMapper deleteLogMapper,
                                          TournamentRegistrationMapper registrationMapper,
                                          UserService userService,
                                          TournamentService tournamentService,
                                          SeriesService seriesService,
                                          SeasonService seasonService,
                                          ITournamentLevelService tournamentLevelService,
                                          TournamentUrlService tournamentUrlService,
                                          TournamentGroupMapper tournamentGroupMapper,
                                          org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        this.messageMapper = messageMapper;
        this.recipientMapper = recipientMapper;
        this.editLogMapper = editLogMapper;
        this.deleteLogMapper = deleteLogMapper;
        this.registrationMapper = registrationMapper;
        this.userService = userService;
        this.tournamentService = tournamentService;
        this.seriesService = seriesService;
        this.seasonService = seasonService;
        this.tournamentLevelService = tournamentLevelService;
        this.tournamentUrlService = tournamentUrlService;
        this.tournamentGroupMapper = tournamentGroupMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotificationMessage createOrUpdate(Long id, String title, String markdown, boolean publishToHome, boolean publishNow,
                                              boolean pinned, String signature, User operator) {
        if (!canManage(operator)) {
            throw new IllegalStateException("无权限管理通知");
        }
        String sig = requireSignature(signature);
        String cleanTitle = title == null ? "" : title.trim();
        if (cleanTitle.isEmpty()) {
            throw new IllegalArgumentException("通知标题不能为空");
        }
        String cleanMd = MarkdownUtils.normalizeMarkdownLineBreaks(markdown == null ? "" : markdown.trim());
        if (cleanMd.isEmpty()) {
            throw new IllegalArgumentException("通知内容不能为空");
        }

        NotificationMessage entity;
        String action;
        String oldTitle = null;
        String oldMd = null;
        Boolean oldHome = null;
        Boolean oldPublished = null;
        Boolean oldPinned = null;
        if (id == null) {
            entity = new NotificationMessage();
            action = "CREATE";
        } else {
            entity = messageMapper.selectById(id);
            if (entity == null) {
                throw new IllegalArgumentException("通知不存在");
            }
            if (!canEditNotification(operator, entity)) {
                throw new IllegalStateException(isAutoGenerated(entity)
                        ? "自动生成的消息通知不可手动编辑"
                        : "无权修改该通知（仅站点管理员或本人创建的手动通知）");
            }
            action = "UPDATE";
            oldTitle = entity.getTitle();
            oldMd = entity.getContentMarkdown();
            oldHome = entity.getPublishToHome();
            oldPublished = entity.getPublished();
            oldPinned = entity.getPinned();
        }
        entity.setTitle(cleanTitle);
        entity.setContentMarkdown(cleanMd);
        entity.setContentHtml(renderHtml(cleanMd));
        entity.setPublishToHome(Boolean.TRUE.equals(publishToHome));
        entity.setPinned(Boolean.TRUE.equals(pinned));
        if (entity.getId() == null) {
            entity.setAuthorUserId(operator != null ? operator.getId() : null);
            // 手动通知不写 sourceType，便于与自动区分
            if (entity.getSourceType() == null) {
                entity.setSourceType(null);
            }
        }
        entity.setPublished(Boolean.TRUE.equals(publishNow));
        if (Boolean.TRUE.equals(publishNow) && entity.getPublishedAt() == null) {
            entity.setPublishedAt(LocalDateTime.now());
        }
        if (entity.getId() == null) {
            messageMapper.insert(entity);
        } else {
            messageMapper.updateById(entity);
        }
        if (Boolean.TRUE.equals(publishNow)) {
            ensureRecipients(entity.getId());
        }
        insertEditLog(entity.getId(), operator, action, oldTitle, cleanTitle, oldMd, cleanMd,
                oldHome, entity.getPublishToHome(), oldPublished, entity.getPublished(),
                oldPinned, entity.getPinned(), sig);
        NotificationMessage saved = messageMapper.selectById(entity.getId());
        attachAuthorUsername(saved);
        attachSendTypeLabel(saved);
        return saved;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNotification(Long id, String signature, User operator) {
        if (!canManage(operator)) {
            throw new IllegalStateException("无权限管理通知");
        }
        String sig = requireSignature(signature);
        if (id == null) {
            throw new IllegalArgumentException("缺少通知ID");
        }
        NotificationMessage entity = messageMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("通知不存在");
        }
        if (!canDeleteNotification(operator, entity)) {
            throw new IllegalStateException("无权删除该通知");
        }
        NotificationDeleteLog del = new NotificationDeleteLog();
        del.setMessageId(entity.getId());
        del.setTitle(entity.getTitle() == null ? "" : entity.getTitle());
        del.setContentMarkdown(entity.getContentMarkdown());
        del.setContentHtml(entity.getContentHtml());
        del.setAuthorUserId(entity.getAuthorUserId());
        del.setSourceType(entity.getSourceType());
        del.setSourceRefId(entity.getSourceRefId());
        del.setPublishToHome(entity.getPublishToHome());
        del.setPublished(entity.getPublished());
        del.setPinned(entity.getPinned());
        del.setPublishedAt(entity.getPublishedAt());
        del.setDeletedByUserId(operator.getId());
        del.setSignature(sig);
        del.setDeletedAt(LocalDateTime.now());
        deleteLogMapper.insert(del);
        messageMapper.deleteById(id);
    }

    @Override
    public List<NotificationEditLog> listEditLogs(Long messageId) {
        if (messageId == null) {
            return List.of();
        }
        List<NotificationEditLog> logs = editLogMapper.selectList(Wrappers.<NotificationEditLog>lambdaQuery()
                .eq(NotificationEditLog::getMessageId, messageId)
                .orderByDesc(NotificationEditLog::getEditedAt)
                .orderByDesc(NotificationEditLog::getId));
        attachEditLogUsernames(logs);
        return logs;
    }

    @Override
    public List<NotificationDeleteLog> listDeleteLogs(int limit) {
        int l = Math.max(1, Math.min(limit <= 0 ? 100 : limit, 500));
        List<NotificationDeleteLog> logs = deleteLogMapper.selectList(Wrappers.<NotificationDeleteLog>lambdaQuery()
                .orderByDesc(NotificationDeleteLog::getDeletedAt)
                .orderByDesc(NotificationDeleteLog::getId)
                .last("LIMIT " + l));
        attachDeleteLogUsernames(logs);
        return logs;
    }

    @Override
    public Optional<NotificationDeleteLog> getDeleteLog(Long deleteLogId) {
        if (deleteLogId == null) {
            return Optional.empty();
        }
        NotificationDeleteLog log = deleteLogMapper.selectById(deleteLogId);
        if (log == null) {
            return Optional.empty();
        }
        attachDeleteLogUsernames(List.of(log));
        return Optional.of(log);
    }

    @Override
    public String buildDeleteLogCopyText(NotificationDeleteLog log) {
        if (log == null) {
            return "";
        }
        String title = log.getTitle() == null ? "未命名通知" : log.getTitle();
        String by = log.getDeletedByUsername() != null ? log.getDeletedByUsername() : "—";
        String author = log.getAuthorUsername() != null ? log.getAuthorUsername() : "—";
        String time = log.getDeletedAt() != null ? COPY_TIME_FORMAT.format(log.getDeletedAt()) : "—";
        String type = (log.getSourceType() != null && !log.getSourceType().isBlank()) ? "自动" : "手动";
        String md = log.getContentMarkdown() == null ? "" : log.getContentMarkdown();
        return "【消息通知删除记录】" + title
                + "\n原通知ID：" + (log.getMessageId() == null ? "—" : log.getMessageId())
                + "\n发送类型：" + type
                + "\n原发起者：" + author
                + "\n删除人：" + by
                + "\n删除时间：" + time
                + "\n\n" + md;
    }

    @Override
    public boolean isAutoGenerated(NotificationMessage message) {
        return message != null && message.getSourceType() != null && !message.getSourceType().isBlank();
    }

    @Override
    public boolean canDeleteNotification(User operator, NotificationMessage message) {
        if (operator == null || message == null || operator.getId() == null) {
            return false;
        }
        if (!canManage(operator)) {
            return false;
        }
        if (isSiteAdmin(operator)) {
            return true;
        }
        if (isAutoGenerated(message)) {
            return false;
        }
        return Objects.equals(message.getAuthorUserId(), operator.getId());
    }

    @Override
    public boolean canTogglePin(User operator, NotificationMessage message) {
        if (operator == null || message == null || operator.getId() == null) {
            return false;
        }
        if (!canManage(operator)) {
            return false;
        }
        if (isSiteAdmin(operator)) {
            return true;
        }
        return Objects.equals(message.getAuthorUserId(), operator.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setPinned(Long id, boolean pinned, String signature, User operator) {
        if (!canManage(operator)) {
            throw new IllegalStateException("无权限管理通知");
        }
        String sig = requireSignature(signature);
        if (id == null) {
            throw new IllegalArgumentException("缺少通知ID");
        }
        NotificationMessage entity = messageMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("通知不存在");
        }
        if (!canTogglePin(operator, entity)) {
            throw new IllegalStateException("无权置顶/取消置顶该通知（仅站点管理员或本人创建的通知）");
        }
        boolean oldPinned = Boolean.TRUE.equals(entity.getPinned());
        boolean newPinned = Boolean.TRUE.equals(pinned);
        if (oldPinned == newPinned) {
            return;
        }
        entity.setPinned(newPinned);
        entity.setUpdatedAt(LocalDateTime.now());
        messageMapper.updateById(entity);
        insertEditLog(entity.getId(), operator, "UPDATE",
                entity.getTitle(), entity.getTitle(),
                entity.getContentMarkdown(), entity.getContentMarkdown(),
                entity.getPublishToHome(), entity.getPublishToHome(),
                entity.getPublished(), entity.getPublished(),
                oldPinned, newPinned, sig);
    }

    private static String requireSignature(String signature) {
        return SignatureValidation.requireNonBlank(signature);
    }

    private void insertEditLog(Long messageId, User operator, String action,
                               String oldTitle, String newTitle,
                               String oldMd, String newMd,
                               Boolean oldHome, Boolean newHome,
                               Boolean oldPublished, Boolean newPublished,
                               Boolean oldPinned, Boolean newPinned,
                               String signature) {
        if (messageId == null) {
            return;
        }
        NotificationEditLog log = new NotificationEditLog();
        log.setMessageId(messageId);
        log.setEditorUserId(operator == null ? null : operator.getId());
        log.setAction(action);
        log.setOldTitle(oldTitle);
        log.setNewTitle(newTitle);
        log.setOldContentMarkdown(oldMd);
        log.setNewContentMarkdown(newMd);
        log.setOldPublishToHome(oldHome);
        log.setNewPublishToHome(newHome);
        log.setOldPublished(oldPublished);
        log.setNewPublished(newPublished);
        log.setOldPinned(oldPinned);
        log.setNewPinned(newPinned);
        log.setSignature(signature);
        log.setEditedAt(LocalDateTime.now());
        editLogMapper.insert(log);
    }

    private void attachEditLogUsernames(List<NotificationEditLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return;
        }
        Set<Long> ids = logs.stream().map(NotificationEditLog::getEditorUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = loadUsernames(ids);
        for (NotificationEditLog log : logs) {
            if (log.getEditorUserId() == null) {
                log.setEditorUsername("—");
            } else {
                log.setEditorUsername(names.getOrDefault(log.getEditorUserId(), "（用户已删除）"));
            }
        }
    }

    private void attachDeleteLogUsernames(List<NotificationDeleteLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return;
        }
        Set<Long> ids = new HashSet<>();
        for (NotificationDeleteLog log : logs) {
            if (log.getDeletedByUserId() != null) {
                ids.add(log.getDeletedByUserId());
            }
            if (log.getAuthorUserId() != null) {
                ids.add(log.getAuthorUserId());
            }
        }
        Map<Long, String> names = loadUsernames(ids);
        for (NotificationDeleteLog log : logs) {
            log.setDeletedByUsername(log.getDeletedByUserId() == null ? "—"
                    : names.getOrDefault(log.getDeletedByUserId(), "（用户已删除）"));
            if (log.getAuthorUserId() == null) {
                log.setAuthorUsername("系统");
            } else {
                log.setAuthorUsername(names.getOrDefault(log.getAuthorUserId(), "（用户已删除）"));
            }
        }
    }

    private Map<Long, String> loadUsernames(Set<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return map;
        }
        for (User u : userService.listByIds(ids)) {
            if (u != null && u.getId() != null && u.getUsername() != null && !u.getUsername().isEmpty()) {
                map.put(u.getId(), u.getUsername());
            }
        }
        return map;
    }

    private void attachSendTypeLabel(NotificationMessage m) {
        if (m == null) {
            return;
        }
        m.setSendTypeLabel(isAutoGenerated(m) ? "自动" : "手动");
    }

    @Override
    public List<NotificationMessage> listInbox(Long userId, int limit) {
        if (userId == null) return List.of();
        int l = Math.max(1, Math.min(limit <= 0 ? 20 : limit, 200));
        List<NotificationRecipient> links = recipientMapper.selectList(Wrappers.<NotificationRecipient>lambdaQuery()
                .eq(NotificationRecipient::getUserId, userId)
                .orderByDesc(NotificationRecipient::getCreatedAt)
                .last("LIMIT " + l));
        if (links.isEmpty()) return List.of();
        List<Long> ids = links.stream().map(NotificationRecipient::getMessageId).filter(Objects::nonNull).distinct().toList();
        List<NotificationMessage> msgs = messageMapper.selectBatchIds(ids);
        msgs.sort(Comparator
                .comparing((NotificationMessage m) -> Boolean.TRUE.equals(m.getPinned()), Comparator.reverseOrder())
                .thenComparing(NotificationMessage::getPublishedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        attachAuthorUsernames(msgs);
        attachTournamentInfo(msgs);
        return msgs;
    }

    @Override
    public List<NotificationMessage> listPublishedForHome(int limit) {
        return listPublicPublished(Math.min(limit <= 0 ? 8 : limit, 50));
    }

    @Override
    public List<NotificationMessage> listPublicPublished(int limit) {
        int l = Math.max(1, Math.min(limit <= 0 ? 40 : limit, 200));
        List<NotificationMessage> list = messageMapper.selectList(Wrappers.<NotificationMessage>lambdaQuery()
                .eq(NotificationMessage::getPublished, true)
                .eq(NotificationMessage::getPublishToHome, true)
                .orderByDesc(NotificationMessage::getPinned)
                .orderByDesc(NotificationMessage::getPublishedAt)
                .last("LIMIT " + l));
        attachAuthorUsernames(list);
        attachTournamentInfo(list);
        return list;
    }

    @Override
    public List<NotificationMessage> listManage(int limit) {
        int l = Math.max(1, Math.min(limit <= 0 ? 30 : limit, 200));
        List<NotificationMessage> list = messageMapper.selectList(Wrappers.<NotificationMessage>lambdaQuery()
                .orderByDesc(NotificationMessage::getPinned)
                .orderByDesc(NotificationMessage::getCreatedAt)
                .last("LIMIT " + l));
        attachAuthorUsernames(list);
        attachTournamentInfo(list);
        return list;
    }

    @Override
    public List<NotificationMessage> listReadableForUser(User viewer, int limit) {
        int l = Math.max(1, Math.min(limit <= 0 ? 40 : limit, 200));
        if (viewer == null || viewer.getId() == null) {
            return listPublicPublished(l);
        }
        // 办赛人员（超管/普管/主办）收件箱与普通用户一致：仅公共通知 + 本人收件；
        // 未参赛时不得因管理员身份看到「分组结果」等参赛者专属通知。管理列表仍走 listManage。
        Set<Long> scopedTournamentIds = resolveScopedTournamentIds(viewer.getId());

        LinkedHashMap<Long, NotificationMessage> byId = new LinkedHashMap<>();
        for (NotificationMessage m : listPublicPublished(Math.min(l, 100))) {
            if (m != null && m.getId() != null) {
                byId.put(m.getId(), m);
            }
        }

        List<NotificationRecipient> links = recipientMapper.selectList(Wrappers.<NotificationRecipient>lambdaQuery()
                .eq(NotificationRecipient::getUserId, viewer.getId())
                .orderByDesc(NotificationRecipient::getCreatedAt)
                .last("LIMIT 500"));
        List<NotificationMessage> recipientMsgs = List.of();
        if (!links.isEmpty()) {
            List<Long> msgIds = links.stream()
                    .map(NotificationRecipient::getMessageId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (!msgIds.isEmpty()) {
                recipientMsgs = messageMapper.selectBatchIds(msgIds);
            }
        }
        Set<Long> knownTournamentIds = resolveKnownTournamentIdsAmong(
                recipientMsgs.stream().map(NotificationMessage::getSourceRefId).filter(Objects::nonNull).collect(Collectors.toSet()));
        for (NotificationMessage m : recipientMsgs) {
            if (m == null || m.getId() == null || !Boolean.TRUE.equals(m.getPublished())) {
                continue;
            }
            if (canReadInternal(m, scopedTournamentIds, knownTournamentIds, true)) {
                byId.putIfAbsent(m.getId(), m);
            }
        }

        List<NotificationMessage> list = new ArrayList<>(byId.values());
        list.sort(Comparator
                .comparing((NotificationMessage m) -> Boolean.TRUE.equals(m.getPinned()), Comparator.reverseOrder())
                .thenComparing(NotificationMessage::getPublishedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(NotificationMessage::getId, Comparator.nullsLast(Comparator.reverseOrder())));
        if (list.size() > l) {
            list = new ArrayList<>(list.subList(0, l));
        }
        attachAuthorUsernames(list);
        attachTournamentInfo(list);
        return list;
    }

    @Override
    public boolean canRead(NotificationMessage message, User viewer) {
        if (message == null || !Boolean.TRUE.equals(message.getPublished())) {
            return false;
        }
        if (viewer == null || viewer.getId() == null) {
            return Boolean.TRUE.equals(message.getPublishToHome());
        }
        // 详情权限与收件箱一致：公共或本人收件；办赛身份不额外放行他人私信
        Set<Long> scoped = resolveScopedTournamentIds(viewer.getId());
        Set<Long> knownTournamentIds = resolveKnownTournamentIdsAmong(
                message.getSourceRefId() == null ? Set.of() : Set.of(message.getSourceRefId()));
        Long cnt = recipientMapper.selectCount(Wrappers.<NotificationRecipient>lambdaQuery()
                .eq(NotificationRecipient::getMessageId, message.getId())
                .eq(NotificationRecipient::getUserId, viewer.getId()));
        boolean isRecipient = cnt != null && cnt > 0;
        return canReadInternal(message, scoped, knownTournamentIds, isRecipient);
    }

    @Override
    public Optional<NotificationMessage> getReadableDetail(Long messageId, User viewer) {
        NotificationMessage m = messageMapper.selectById(messageId);
        if (m == null || !Boolean.TRUE.equals(m.getPublished())) {
            return Optional.empty();
        }
        if (!canRead(m, viewer)) {
            return Optional.empty();
        }
        m.setContentHtml(MarkdownUtils.markdownToSafeHtml(m.getContentMarkdown()));
        if (viewer != null && viewer.getId() != null) {
            markRead(messageId, viewer.getId());
        }
        attachAuthorUsername(m);
        attachTournamentInfo(List.of(m));
        return Optional.of(m);
    }

    @Override
    public Optional<NotificationMessage> getManageDetail(Long messageId, User operator) {
        if (messageId == null || !canManage(operator)) return Optional.empty();
        NotificationMessage m = messageMapper.selectById(messageId);
        if (m == null || !canEditNotification(operator, m)) return Optional.empty();
        attachAuthorUsername(m);
        attachTournamentInfo(List.of(m));
        return Optional.of(m);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long messageId, Long userId) {
        if (messageId == null || userId == null) return;
        NotificationRecipient link = recipientMapper.selectOne(Wrappers.<NotificationRecipient>lambdaQuery()
                .eq(NotificationRecipient::getMessageId, messageId)
                .eq(NotificationRecipient::getUserId, userId));
        if (link == null) return;
        if (link.getReadAt() == null) {
            link.setReadAt(LocalDateTime.now());
            recipientMapper.updateById(link);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markUnread(Long messageId, Long userId) {
        if (messageId == null || userId == null) return;
        NotificationRecipient link = recipientMapper.selectOne(Wrappers.<NotificationRecipient>lambdaQuery()
                .eq(NotificationRecipient::getMessageId, messageId)
                .eq(NotificationRecipient::getUserId, userId)
                .last("LIMIT 1"));
        if (link == null) return;
        if (link.getReadAt() != null) {
            link.setReadAt(null);
            recipientMapper.updateById(link);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markAllRead(Long userId) {
        if (userId == null) {
            return 0;
        }
        return recipientMapper.update(null, Wrappers.<NotificationRecipient>lambdaUpdate()
                .eq(NotificationRecipient::getUserId, userId)
                .isNull(NotificationRecipient::getReadAt)
                .set(NotificationRecipient::getReadAt, LocalDateTime.now()));
    }

    @Override
    public long unreadCount(Long userId) {
        if (userId == null) return 0L;
        return recipientMapper.selectCount(Wrappers.<NotificationRecipient>lambdaQuery()
                .eq(NotificationRecipient::getUserId, userId)
                .isNull(NotificationRecipient::getReadAt));
    }

    @Override
    public boolean canManage(User user) {
        if (user == null || user.getRole() == null) return false;
        if (isSiteAdmin(user)) return true;
        if (user.getId() == null) return false;
        return tournamentService.lambdaQuery()
                .eq(com.example.entity.Tournament::getHostUserId, user.getId())
                .last("LIMIT 1")
                .one() != null;
    }

    private static boolean isSiteAdmin(User user) {
        return user != null && user.getRole() != null
                && (user.getRole() == ROLE_SUPER_ADMIN || user.getRole() == ROLE_ADMIN);
    }

    /**
     * 当前已报名（未拒绝）+ 本人主办的赛事，用于专属通知范围。
     */
    private Set<Long> resolveScopedTournamentIds(Long userId) {
        Set<Long> ids = new HashSet<>();
        if (userId == null) {
            return ids;
        }
        List<TournamentRegistration> regs = registrationMapper.selectList(Wrappers.<TournamentRegistration>lambdaQuery()
                .eq(TournamentRegistration::getUserId, userId)
                .and(w -> w.isNull(TournamentRegistration::getStatus)
                        .or()
                        .ne(TournamentRegistration::getStatus, 2)));
        for (TournamentRegistration r : regs) {
            if (r != null && r.getTournamentId() != null) {
                ids.add(r.getTournamentId());
            }
        }
        List<Tournament> hosted = tournamentService.lambdaQuery()
                .eq(Tournament::getHostUserId, userId)
                .list();
        for (Tournament t : hosted) {
            if (t != null && t.getId() != null) {
                ids.add(t.getId());
            }
        }
        return ids;
    }

    private Set<Long> resolveKnownTournamentIdsAmong(Set<Long> candidateIds) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return Set.of();
        }
        return tournamentService.listByIds(candidateIds).stream()
                .filter(Objects::nonNull)
                .map(Tournament::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * 普通用户可读规则：
     * - 公共首页通知；
     * - 本人收件（含办赛汇总、对阵/积分等定向通知；显式收件即有权阅读，勿再卡报名/主办范围）；
     * 列表侧不按赛事 ID 拉取他人定向通知，避免泄露分组等私信。
     */
    private boolean canReadInternal(NotificationMessage message,
                                    Set<Long> scopedTournamentIds,
                                    Set<Long> knownTournamentIds,
                                    boolean isRecipient) {
        if (message == null || !Boolean.TRUE.equals(message.getPublished())) {
            return false;
        }
        if (Boolean.TRUE.equals(message.getPublishToHome())) {
            return true;
        }
        // scopedTournamentIds / knownTournamentIds 保留参数供列表组装侧使用；读权限以收件关系为准
        return isRecipient;
    }

    @Override
    public boolean canEditNotification(User operator, NotificationMessage message) {
        if (operator == null || message == null || operator.getId() == null) {
            return false;
        }
        if (!canManage(operator)) {
            return false;
        }
        if (isAutoGenerated(message)) {
            return false;
        }
        if (isSiteAdmin(operator)) {
            return true;
        }
        return Objects.equals(message.getAuthorUserId(), operator.getId());
    }

    @Override
    public String buildCopyText(NotificationMessage message) {
        if (message == null) return "";
        attachAuthorUsername(message);
        String title = message.getTitle() == null ? "未命名通知" : message.getTitle();
        String author = message.getAuthorUsername() != null ? message.getAuthorUsername() : "—";
        String time = message.getPublishedAt() != null
                ? COPY_TIME_FORMAT.format(message.getPublishedAt())
                : "—";
        String md = message.getContentMarkdown() == null ? "" : message.getContentMarkdown();
        return "【消息通知】" + title + "\n发起者：" + author + "\n发布时间：" + time + "\n\n" + md;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendSystemNotification(String title, String markdown, String sourceType, Long sourceRefId) {
        NotificationMessage exists = messageMapper.selectOne(Wrappers.<NotificationMessage>lambdaQuery()
                .eq(NotificationMessage::getSourceType, sourceType)
                .eq(NotificationMessage::getSourceRefId, sourceRefId)
                .eq(NotificationMessage::getPublished, true)
                .last("LIMIT 1"));
        if (exists != null) return;

        User superAdmin = userService.lambdaQuery()
                .eq(User::getRole, ROLE_SUPER_ADMIN)
                .last("LIMIT 1")
                .one();
        Long authorId = superAdmin != null ? superAdmin.getId() : null;

        NotificationMessage m = new NotificationMessage();
        m.setTitle(title == null ? "系统通知" : title.trim());
        m.setContentMarkdown(MarkdownUtils.normalizeMarkdownLineBreaks(markdown == null ? "" : markdown.trim()));
        m.setContentHtml(renderHtml(m.getContentMarkdown()));
        m.setPublished(true);
        m.setPublishToHome(true);
        m.setAuthorUserId(authorId);
        m.setSourceType(sourceType);
        m.setSourceRefId(sourceRefId);
        m.setPublishedAt(LocalDateTime.now());
        messageMapper.insert(m);
        ensureRecipients(m.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendNotificationToUserIds(String title, String markdown, String sourceType, Long sourceRefId,
                                        Collection<Long> recipientUserIds) {
        if (recipientUserIds == null || recipientUserIds.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> ids = recipientUserIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return;
        }

        NotificationMessage exists = messageMapper.selectOne(Wrappers.<NotificationMessage>lambdaQuery()
                .eq(NotificationMessage::getSourceType, sourceType)
                .eq(NotificationMessage::getSourceRefId, sourceRefId)
                .eq(NotificationMessage::getPublished, true)
                .last("LIMIT 1"));
        if (exists != null) {
            return;
        }

        User superAdmin = userService.lambdaQuery()
                .eq(User::getRole, ROLE_SUPER_ADMIN)
                .last("LIMIT 1")
                .one();
        Long authorId = superAdmin != null ? superAdmin.getId() : null;

        NotificationMessage m = new NotificationMessage();
        m.setTitle(title == null ? "系统通知" : title.trim());
        m.setContentMarkdown(MarkdownUtils.normalizeMarkdownLineBreaks(markdown == null ? "" : markdown.trim()));
        m.setContentHtml(renderHtml(m.getContentMarkdown()));
        m.setPublished(true);
        m.setPublishToHome(false);
        m.setAuthorUserId(authorId);
        m.setSourceType(sourceType);
        m.setSourceRefId(sourceRefId);
        m.setPublishedAt(LocalDateTime.now());
        messageMapper.insert(m);

        for (Long uid : ids) {
            NotificationRecipient nr = new NotificationRecipient();
            nr.setMessageId(m.getId());
            nr.setUserId(uid);
            recipientMapper.insert(nr);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendPersonalNotification(String title, String markdown, String sourceType, Long sourceRefId, Long userId) {
        if (userId == null || sourceType == null || sourceType.isBlank() || sourceRefId == null) {
            return;
        }
        List<NotificationMessage> existing = messageMapper.selectList(Wrappers.<NotificationMessage>lambdaQuery()
                .eq(NotificationMessage::getSourceType, sourceType)
                .eq(NotificationMessage::getSourceRefId, sourceRefId)
                .eq(NotificationMessage::getPublished, true));
        for (NotificationMessage old : existing) {
            if (old == null || old.getId() == null) {
                continue;
            }
            Long cnt = recipientMapper.selectCount(Wrappers.<NotificationRecipient>lambdaQuery()
                    .eq(NotificationRecipient::getMessageId, old.getId())
                    .eq(NotificationRecipient::getUserId, userId));
            if (cnt != null && cnt > 0) {
                return;
            }
        }
        User superAdmin = userService.lambdaQuery()
                .eq(User::getRole, ROLE_SUPER_ADMIN)
                .last("LIMIT 1")
                .one();
        NotificationMessage m = new NotificationMessage();
        m.setTitle(title == null || title.isBlank() ? "系统通知" : title.trim());
        m.setContentMarkdown(MarkdownUtils.normalizeMarkdownLineBreaks(markdown == null ? "" : markdown.trim()));
        m.setContentHtml(renderHtml(m.getContentMarkdown()));
        m.setPublished(true);
        m.setPublishToHome(false);
        m.setAuthorUserId(superAdmin != null ? superAdmin.getId() : null);
        m.setSourceType(sourceType);
        m.setSourceRefId(sourceRefId);
        m.setPublishedAt(LocalDateTime.now());
        messageMapper.insert(m);
        NotificationRecipient nr = new NotificationRecipient();
        nr.setMessageId(m.getId());
        nr.setUserId(userId);
        recipientMapper.insert(nr);
    }

    @Transactional(rollbackFor = Exception.class)
    public void ensureRecipients(Long messageId) {
        if (messageId == null) return;
        List<User> users = userService.list();
        for (User u : users) {
            if (u == null || u.getId() == null) continue;
            NotificationRecipient exists = recipientMapper.selectOne(Wrappers.<NotificationRecipient>lambdaQuery()
                    .eq(NotificationRecipient::getMessageId, messageId)
                    .eq(NotificationRecipient::getUserId, u.getId())
                    .last("LIMIT 1"));
            if (exists != null) continue;
            NotificationRecipient n = new NotificationRecipient();
            n.setMessageId(messageId);
            n.setUserId(u.getId());
            recipientMapper.insert(n);
        }
    }

    private String renderHtml(String markdown) {
        return MarkdownUtils.markdownToSafeHtml(markdown);
    }

    private void attachAuthorUsername(NotificationMessage m) {
        if (m == null) {
            return;
        }
        attachAuthorUsernames(List.of(m));
    }

    private void attachAuthorUsernames(List<NotificationMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        Set<Long> authorIds = messages.stream()
                .map(NotificationMessage::getAuthorUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> idToName = new HashMap<>();
        if (!authorIds.isEmpty()) {
            List<User> users = userService.listByIds(authorIds);
            for (User u : users) {
                if (u != null && u.getId() != null && u.getUsername() != null && !u.getUsername().isEmpty()) {
                    idToName.put(u.getId(), u.getUsername());
                }
            }
        }
        for (NotificationMessage m : messages) {
            Long aid = m.getAuthorUserId();
            if (aid == null) {
                m.setAuthorUsername("系统");
            } else {
                String name = idToName.get(aid);
                m.setAuthorUsername(name != null ? name : "（用户已删除）");
            }
            attachSendTypeLabel(m);
        }
    }

    private void attachTournamentInfo(List<NotificationMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        // T_GE_G 的 source_ref_id 是 groupId，需先解析为 tournamentId（历史通知一并生效）
        Map<Long, Long> groupIdToTournamentId = new HashMap<>();
        Set<Long> groupIds = messages.stream()
                .filter(m -> m != null && "T_GE_G".equals(m.getSourceType()) && m.getSourceRefId() != null)
                .map(NotificationMessage::getSourceRefId)
                .collect(Collectors.toSet());
        if (!groupIds.isEmpty()) {
            for (TournamentGroup g : tournamentGroupMapper.selectBatchIds(groupIds)) {
                if (g != null && g.getId() != null && g.getTournamentId() != null) {
                    groupIdToTournamentId.put(g.getId(), g.getTournamentId());
                }
            }
        }

        Set<Long> tidSet = new HashSet<>();
        for (NotificationMessage m : messages) {
            Long tid = resolveTournamentIdForAttach(m, groupIdToTournamentId);
            if (tid != null) {
                tidSet.add(tid);
            }
        }
        if (tidSet.isEmpty()) {
            return;
        }

        Map<Long, Tournament> tournamentById = tournamentService.listByIds(tidSet).stream()
                .filter(Objects::nonNull)
                .filter(t -> t.getId() != null)
                .collect(Collectors.toMap(Tournament::getId, t -> t, (a, b) -> a));
        Map<Long, Series> seriesById = seriesService.list().stream()
                .filter(Objects::nonNull)
                .filter(s -> s.getId() != null)
                .collect(Collectors.toMap(Series::getId, s -> s, (a, b) -> a));
        Map<Long, Season> seasonById = seasonService.list().stream()
                .filter(Objects::nonNull)
                .filter(s -> s.getId() != null)
                .collect(Collectors.toMap(Season::getId, s -> s, (a, b) -> a));
        Map<String, String> levelNameByCode = tournamentLevelService.list().stream()
                .filter(Objects::nonNull)
                .filter(l -> l.getCode() != null)
                .collect(Collectors.toMap(TournamentLevel::getCode, TournamentLevel::getName, (a, b) -> a));

        Map<String, Map<Long, Integer>> editionCache = new HashMap<>();
        for (NotificationMessage m : messages) {
            Long tid = resolveTournamentIdForAttach(m, groupIdToTournamentId);
            if (tid == null) {
                continue;
            }
            Tournament t = tournamentById.get(tid);
            if (t == null) {
                continue;
            }
            Series ser = t.getSeriesId() == null ? null : seriesById.get(t.getSeriesId());
            Season season = (ser == null || ser.getSeasonId() == null) ? null : seasonById.get(ser.getSeasonId());
            String seasonLabel = season == null ? "赛季" : (season.getYear() + "年" + (Objects.equals(season.getHalf(), 1) ? "上半年" : "下半年"));
            String level = levelNameByCode.getOrDefault(t.getLevelCode(), t.getLevelCode() == null ? "赛事等级" : t.getLevelCode());

            Integer edition = null;
            if (season != null && t.getLevelCode() != null) {
                String k = season.getId() + "|" + t.getLevelCode();
                Map<Long, Integer> byTid = editionCache.get(k);
                if (byTid == null) {
                    List<Long> seasonSeriesIds = seriesService.lambdaQuery()
                            .eq(Series::getSeasonId, season.getId())
                            .list()
                            .stream()
                            .filter(ss -> !isTestSeries(ss))
                            .map(Series::getId)
                            .filter(Objects::nonNull)
                            .toList();
                    byTid = new HashMap<>();
                    if (!seasonSeriesIds.isEmpty()) {
                        List<Tournament> sameLevel = tournamentService.lambdaQuery()
                                .in(Tournament::getSeriesId, seasonSeriesIds)
                                .eq(Tournament::getLevelCode, t.getLevelCode())
                                .list();
                        sameLevel.sort(Comparator.comparing(Tournament::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(Tournament::getId, Comparator.nullsLast(Comparator.naturalOrder())));
                        for (int i = 0; i < sameLevel.size(); i++) {
                            Tournament x = sameLevel.get(i);
                            if (x.getId() != null) {
                                byTid.put(x.getId(), i + 1);
                            }
                        }
                    }
                    editionCache.put(k, byTid);
                }
                edition = byTid.get(t.getId());
            }
            m.setTournamentEditionLabel(seasonLabel + "-" + level + "-" + (edition == null ? "?" : edition));
            m.setTournamentDetailUrl(tournamentUrlService.detailUrl(t));
        }
    }

    /** 列表/详情关联赛事：多数 source_ref_id 即 tournamentId；T_GE_G 存的是 groupId。 */
    private static Long resolveTournamentIdForAttach(NotificationMessage m, Map<Long, Long> groupIdToTournamentId) {
        if (m == null || m.getSourceRefId() == null) {
            return null;
        }
        if ("T_GE_G".equals(m.getSourceType())) {
            return groupIdToTournamentId.get(m.getSourceRefId());
        }
        return m.getSourceRefId();
    }

    private static boolean isTestSeries(Series series) {
        if (series == null) return false;
        String name = series.getName();
        return name != null && name.contains("测试");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendForgotPasswordNotification(Long requesterUserId) {
        if (requesterUserId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        User requester = userService.getById(requesterUserId);
        if (requester == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        // 检查是否已存在未处理的忘记密码请求
        NotificationMessage exists = messageMapper.selectOne(Wrappers.<NotificationMessage>lambdaQuery()
                .eq(NotificationMessage::getSourceType, "FORGOT_PASSWORD")
                .eq(NotificationMessage::getSourceRefId, requesterUserId)
                .eq(NotificationMessage::getPublished, true)
                .last("LIMIT 1"));
        
        if (exists != null) {
            // 检查是否还有待处理的管理员
            List<NotificationRecipient> pendingRecipients = recipientMapper.selectList(Wrappers.<NotificationRecipient>lambdaQuery()
                    .eq(NotificationRecipient::getMessageId, exists.getId())
                    .eq(NotificationRecipient::getResetPasswordStatus, "PENDING"));
            
            if (!pendingRecipients.isEmpty()) {
                throw new IllegalStateException("您已提交过忘记密码请求，请等待管理员处理");
            }
        }

        // 获取所有管理员
        List<User> admins = userService.lambdaQuery()
                .in(User::getRole, ROLE_SUPER_ADMIN, ROLE_ADMIN)
                .list();

        if (admins.isEmpty()) {
            throw new IllegalStateException("系统中没有管理员，请联系系统负责人");
        }

        // 创建通知消息
        NotificationMessage message = new NotificationMessage();
        message.setTitle("忘记密码请求");
        message.setContentMarkdown(String.format(
                "用户 **%s** 发起了忘记密码请求，请为其重置密码。\n\n发起时间：%s",
                requester.getUsername(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        ));
        message.setContentHtml(renderHtml(message.getContentMarkdown()));
        message.setPublished(true);
        message.setPublishToHome(false);
        message.setAuthorUserId(requesterUserId);
        message.setSourceType("FORGOT_PASSWORD");
        message.setSourceRefId(requesterUserId);
        message.setPublishedAt(LocalDateTime.now());
        messageMapper.insert(message);

        // 为每个管理员创建接收记录，初始状态为 PENDING
        for (User admin : admins) {
            NotificationRecipient recipient = new NotificationRecipient();
            recipient.setMessageId(message.getId());
            recipient.setUserId(admin.getId());
            recipient.setResetPasswordStatus("PENDING");
            recipientMapper.insert(recipient);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetUserPassword(Long messageId, Long adminUserId, Long targetUserId) {
        if (messageId == null || adminUserId == null || targetUserId == null) {
            throw new IllegalArgumentException("参数不能为空");
        }

        // 验证管理员权限
        User admin = userService.getById(adminUserId);
        if (admin == null || (admin.getRole() != ROLE_SUPER_ADMIN && admin.getRole() != ROLE_ADMIN)) {
            throw new IllegalStateException("无权限执行此操作");
        }

        // 验证目标用户
        User targetUser = userService.getById(targetUserId);
        if (targetUser == null) {
            throw new IllegalArgumentException("目标用户不存在");
        }

        // 验证消息
        NotificationMessage message = messageMapper.selectById(messageId);
        if (message == null || !"FORGOT_PASSWORD".equals(message.getSourceType()) || 
            !targetUserId.equals(message.getSourceRefId())) {
            throw new IllegalStateException("无效的忘记密码请求");
        }

        // 检查该管理员是否已经处理过此请求
        NotificationRecipient myRecipient = recipientMapper.selectOne(Wrappers.<NotificationRecipient>lambdaQuery()
                .eq(NotificationRecipient::getMessageId, messageId)
                .eq(NotificationRecipient::getUserId, adminUserId)
                .last("LIMIT 1"));
        
        if (myRecipient != null && "RESET_BY_ME".equals(myRecipient.getResetPasswordStatus())) {
            throw new IllegalStateException("您已经重置过该用户的密码");
        }

        // 重置密码为默认密码 123456
        String defaultPassword = "123456";
        targetUser.setPassword(passwordEncoder.encode(defaultPassword));
        targetUser.setPasswordChanged(true); // 设置为需要修改密码
        targetUser.setUpdatedAt(LocalDateTime.now());
        userService.updateById(targetUser);

        // 更新所有管理员的状态
        List<NotificationRecipient> allRecipients = recipientMapper.selectList(Wrappers.<NotificationRecipient>lambdaQuery()
                .eq(NotificationRecipient::getMessageId, messageId));

        for (NotificationRecipient recipient : allRecipients) {
            if (recipient.getUserId().equals(adminUserId)) {
                // 执行操作的管理员
                recipient.setResetPasswordStatus("RESET_BY_ME");
                recipient.setResetPasswordByUserId(adminUserId);
                recipient.setResetPasswordAt(LocalDateTime.now());
            } else {
                // 其他管理员
                recipient.setResetPasswordStatus("RESET_BY_OTHER");
                recipient.setResetPasswordByUserId(adminUserId);
                recipient.setResetPasswordAt(LocalDateTime.now());
            }
            recipientMapper.updateById(recipient);
        }
    }

    @Override
    public List<NotificationRecipient> getResetPasswordRecipients(Long messageId) {
        if (messageId == null) return List.of();
        
        List<NotificationRecipient> recipients = recipientMapper.selectList(Wrappers.<NotificationRecipient>lambdaQuery()
                .eq(NotificationRecipient::getMessageId, messageId));
        
        // 填充管理员用户名
        Set<Long> adminIds = recipients.stream()
                .map(NotificationRecipient::getResetPasswordByUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        
        if (!adminIds.isEmpty()) {
            Map<Long, String> adminNames = userService.listByIds(adminIds).stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
            
            for (NotificationRecipient recipient : recipients) {
                if (recipient.getResetPasswordByUserId() != null) {
                    recipient.setResetPasswordByUsername(adminNames.get(recipient.getResetPasswordByUserId()));
                }
            }
        }
        
        return recipients;
    }

    @Override
    public Optional<NotificationRecipient> getResetPasswordRecipientStatus(Long messageId, Long userId) {
        if (messageId == null || userId == null) return Optional.empty();
        
        NotificationRecipient recipient = recipientMapper.selectOne(Wrappers.<NotificationRecipient>lambdaQuery()
                .eq(NotificationRecipient::getMessageId, messageId)
                .eq(NotificationRecipient::getUserId, userId)
                .last("LIMIT 1"));
        
        if (recipient != null && recipient.getResetPasswordByUserId() != null) {
            User admin = userService.getById(recipient.getResetPasswordByUserId());
            if (admin != null) {
                recipient.setResetPasswordByUsername(admin.getUsername());
            }
        }
        
        return Optional.ofNullable(recipient);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePublishedContent(Long messageId, String markdown) {
        if (messageId == null) {
            return;
        }
        NotificationMessage m = messageMapper.selectById(messageId);
        if (m == null) {
            return;
        }
        String md = MarkdownUtils.normalizeMarkdownLineBreaks(markdown == null ? "" : markdown.trim());
        m.setContentMarkdown(md);
        m.setContentHtml(renderHtml(md));
        messageMapper.updateById(m);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int reRenderAllPublishedHtml() {
        List<NotificationMessage> list = messageMapper.selectList(Wrappers.<NotificationMessage>lambdaQuery()
                .eq(NotificationMessage::getPublished, true)
                .isNotNull(NotificationMessage::getContentMarkdown));
        if (list == null || list.isEmpty()) {
            return 0;
        }
        int updated = 0;
        for (NotificationMessage m : list) {
            if (m == null || m.getId() == null) {
                continue;
            }
            String html = renderHtml(m.getContentMarkdown());
            if (Objects.equals(html, m.getContentHtml())) {
                continue;
            }
            m.setContentHtml(html);
            messageMapper.updateById(m);
            updated++;
        }
        return updated;
    }

    @Override
    public boolean hasPublishedBySource(String sourceType, Long sourceRefId) {
        if (sourceType == null || sourceType.isBlank() || sourceRefId == null) {
            return false;
        }
        Long cnt = messageMapper.selectCount(Wrappers.<NotificationMessage>lambdaQuery()
                .eq(NotificationMessage::getSourceType, sourceType)
                .eq(NotificationMessage::getSourceRefId, sourceRefId)
                .eq(NotificationMessage::getPublished, true));
        return cnt != null && cnt > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceRecipients(Long messageId, Collection<Long> userIds) {
        if (messageId == null) {
            return;
        }
        LinkedHashSet<Long> wanted = new LinkedHashSet<>();
        if (userIds != null) {
            for (Long uid : userIds) {
                if (uid != null) {
                    wanted.add(uid);
                }
            }
        }
        List<NotificationRecipient> existing = recipientMapper.selectList(Wrappers.<NotificationRecipient>lambdaQuery()
                .eq(NotificationRecipient::getMessageId, messageId));
        Map<Long, NotificationRecipient> byUser = new HashMap<>();
        if (existing != null) {
            for (NotificationRecipient r : existing) {
                if (r == null || r.getUserId() == null) {
                    continue;
                }
                byUser.put(r.getUserId(), r);
                if (!wanted.contains(r.getUserId())) {
                    recipientMapper.deleteById(r.getId());
                }
            }
        }
        for (Long uid : wanted) {
            if (byUser.containsKey(uid)) {
                continue;
            }
            NotificationRecipient nr = new NotificationRecipient();
            nr.setMessageId(messageId);
            nr.setUserId(uid);
            recipientMapper.insert(nr);
        }
    }
}
