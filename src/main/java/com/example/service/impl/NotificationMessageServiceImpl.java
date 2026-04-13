package com.example.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.entity.NotificationMessage;
import com.example.entity.NotificationRecipient;
import com.example.entity.Season;
import com.example.entity.Series;
import com.example.entity.Tournament;
import com.example.entity.TournamentLevel;
import com.example.entity.User;
import com.example.mapper.NotificationMessageMapper;
import com.example.mapper.NotificationRecipientMapper;
import com.example.service.INotificationService;
import com.example.service.ITournamentLevelService;
import com.example.service.SeasonService;
import com.example.service.SeriesService;
import com.example.service.TournamentService;
import com.example.service.UserService;
import com.example.util.MarkdownUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
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
    private final UserService userService;
    private final TournamentService tournamentService;
    private final SeriesService seriesService;
    private final SeasonService seasonService;
    private final ITournamentLevelService tournamentLevelService;

    public NotificationMessageServiceImpl(NotificationMessageMapper messageMapper,
                                          NotificationRecipientMapper recipientMapper,
                                          UserService userService,
                                          TournamentService tournamentService,
                                          SeriesService seriesService,
                                          SeasonService seasonService,
                                          ITournamentLevelService tournamentLevelService) {
        this.messageMapper = messageMapper;
        this.recipientMapper = recipientMapper;
        this.userService = userService;
        this.tournamentService = tournamentService;
        this.seriesService = seriesService;
        this.seasonService = seasonService;
        this.tournamentLevelService = tournamentLevelService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotificationMessage createOrUpdate(Long id, String title, String markdown, boolean publishToHome, boolean publishNow, User operator) {
        if (!canManage(operator)) {
            throw new IllegalStateException("无权限管理通知");
        }
        String cleanTitle = title == null ? "" : title.trim();
        if (cleanTitle.isEmpty()) {
            throw new IllegalArgumentException("通知标题不能为空");
        }
        String cleanMd = MarkdownUtils.normalizeMarkdownLineBreaks(markdown == null ? "" : markdown.trim());
        if (cleanMd.isEmpty()) {
            throw new IllegalArgumentException("通知内容不能为空");
        }

        NotificationMessage entity;
        if (id == null) {
            entity = new NotificationMessage();
        } else {
            entity = messageMapper.selectById(id);
            if (entity == null) {
                throw new IllegalArgumentException("通知不存在");
            }
            if (!canEditNotification(operator, entity)) {
                throw new IllegalStateException("只有创建此通知的用户可以修改");
            }
        }
        entity.setTitle(cleanTitle);
        entity.setContentMarkdown(cleanMd);
        entity.setContentHtml(renderHtml(cleanMd));
        entity.setPublishToHome(Boolean.TRUE.equals(publishToHome));
        if (entity.getId() == null) {
        entity.setAuthorUserId(operator != null ? operator.getId() : null);
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
        NotificationMessage saved = messageMapper.selectById(entity.getId());
        attachAuthorUsername(saved);
        return saved;
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
        msgs.sort(Comparator.comparing(NotificationMessage::getPublishedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        attachAuthorUsernames(msgs);
        attachTournamentInfo(msgs);
        return msgs;
    }

    @Override
    public List<NotificationMessage> listPublishedForHome(int limit) {
        int l = Math.max(1, Math.min(limit <= 0 ? 8 : limit, 50));
        List<NotificationMessage> list = messageMapper.selectList(Wrappers.<NotificationMessage>lambdaQuery()
                .eq(NotificationMessage::getPublished, true)
                .eq(NotificationMessage::getPublishToHome, true)
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
                .orderByDesc(NotificationMessage::getCreatedAt)
                .last("LIMIT " + l));
        attachAuthorUsernames(list);
        attachTournamentInfo(list);
        return list;
    }

    @Override
    public Optional<NotificationMessage> getReadableDetail(Long messageId, Long viewerUserId) {
        NotificationMessage m = messageMapper.selectById(messageId);
        if (m == null || !Boolean.TRUE.equals(m.getPublished())) return Optional.empty();
        m.setContentHtml(MarkdownUtils.markdownToSafeHtml(m.getContentMarkdown()));
        if (viewerUserId != null) {
            markRead(messageId, viewerUserId);
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
    public long unreadCount(Long userId) {
        if (userId == null) return 0L;
        return recipientMapper.selectCount(Wrappers.<NotificationRecipient>lambdaQuery()
                .eq(NotificationRecipient::getUserId, userId)
                .isNull(NotificationRecipient::getReadAt));
    }

    @Override
    public boolean canManage(User user) {
        if (user == null || user.getRole() == null) return false;
        if (user.getRole() == ROLE_SUPER_ADMIN || user.getRole() == ROLE_ADMIN) return true;
        if (user.getId() == null) return false;
        return tournamentService.lambdaQuery()
                .eq(com.example.entity.Tournament::getHostUserId, user.getId())
                .last("LIMIT 1")
                .one() != null;
    }

    @Override
    public boolean canEditNotification(User operator, NotificationMessage message) {
        if (operator == null || message == null || operator.getId() == null) {
            return false;
        }
        if (!canManage(operator)) {
            return false;
        }
        Long authorId = message.getAuthorUserId();
        if (authorId == null) {
            return operator.getRole() != null && operator.getRole() == ROLE_SUPER_ADMIN;
        }
        return authorId.equals(operator.getId());
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
        }
    }

    private void attachTournamentInfo(List<NotificationMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        Set<Long> tidSet = messages.stream()
                .map(NotificationMessage::getSourceRefId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
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
            Long tid = m.getSourceRefId();
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
            m.setTournamentDetailUrl("/tournament/detail/" + t.getId());
        }
    }

    private static boolean isTestSeries(Series series) {
        if (series == null) return false;
        String name = series.getName();
        return name != null && name.contains("测试");
    }
}
