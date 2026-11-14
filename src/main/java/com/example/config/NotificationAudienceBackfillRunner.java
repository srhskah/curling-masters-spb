package com.example.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.entity.Match;
import com.example.entity.NotificationMessage;
import com.example.entity.NotificationRecipient;
import com.example.entity.Tournament;
import com.example.entity.TournamentGroup;
import com.example.mapper.NotificationMessageMapper;
import com.example.mapper.NotificationRecipientMapper;
import com.example.mapper.TournamentGroupMapper;
import com.example.service.IMatchService;
import com.example.service.INotificationService;
import com.example.service.SystemConfigService;
import com.example.service.TournamentService;
import com.example.service.impl.TournamentLifecycleNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 一次性纠偏通知受众：环节汇总发给全体办赛人员（含已参赛）；
 * 个人向去掉未参赛办赛人员；抽签开始/完成并入全体办赛人员。
 */
@Component
@Order(200)
public class NotificationAudienceBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NotificationAudienceBackfillRunner.class);
    private static final String FLAG_KEY = "notification_audience_backfill_v2";

    private final SystemConfigService systemConfigService;
    private final INotificationService notificationService;
    private final NotificationMessageMapper messageMapper;
    private final NotificationRecipientMapper recipientMapper;
    private final TournamentLifecycleNotificationService lifecycle;
    private final TournamentService tournamentService;
    private final TournamentGroupMapper groupMapper;
    private final IMatchService matchService;

    public NotificationAudienceBackfillRunner(SystemConfigService systemConfigService,
                                              INotificationService notificationService,
                                              NotificationMessageMapper messageMapper,
                                              NotificationRecipientMapper recipientMapper,
                                              TournamentLifecycleNotificationService lifecycle,
                                              TournamentService tournamentService,
                                              TournamentGroupMapper groupMapper,
                                              IMatchService matchService) {
        this.systemConfigService = systemConfigService;
        this.notificationService = notificationService;
        this.messageMapper = messageMapper;
        this.recipientMapper = recipientMapper;
        this.lifecycle = lifecycle;
        this.tournamentService = tournamentService;
        this.groupMapper = groupMapper;
        this.matchService = matchService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if ("done".equalsIgnoreCase(systemConfigService.getConfigValue(FLAG_KEY, ""))) {
            return;
        }
        int staffFixed = 0;
        int drawFixed = 0;
        int personalTrimmed = 0;
        try {
            List<NotificationMessage> all = messageMapper.selectList(Wrappers.<NotificationMessage>lambdaQuery()
                    .eq(NotificationMessage::getPublished, true)
                    .isNotNull(NotificationMessage::getSourceType));
            if (all == null) {
                all = List.of();
            }
            for (NotificationMessage m : all) {
                if (m == null || m.getId() == null || m.getSourceType() == null) {
                    continue;
                }
                String st = m.getSourceType();
                Long tid = resolveTournamentId(m);
                if (tid == null) {
                    continue;
                }
                Tournament tournament = tournamentService.getById(tid);
                if (tournament == null) {
                    continue;
                }
                Set<Long> participants = lifecycle.participantUserIds(tid);
                Set<Long> organizers = lifecycle.organizerUserIds(tournament);

                if (isStaffSummary(st)) {
                    notificationService.replaceRecipients(m.getId(), organizers);
                    staffFixed++;
                    continue;
                }
                if ("DRAW_OPEN".equals(st) || "DRAW_COMPLETE".equals(st)) {
                    Set<Long> existing = loadRecipientUserIds(m.getId());
                    Set<Long> merged = new LinkedHashSet<>(existing);
                    merged.addAll(organizers);
                    notificationService.replaceRecipients(m.getId(), merged);
                    drawFixed++;
                    continue;
                }
                if (isPersonal(st)) {
                    List<NotificationRecipient> links = recipientMapper.selectList(
                            Wrappers.<NotificationRecipient>lambdaQuery()
                                    .eq(NotificationRecipient::getMessageId, m.getId()));
                    if (links == null || links.isEmpty()) {
                        continue;
                    }
                    Set<Long> keep = new LinkedHashSet<>();
                    boolean changed = false;
                    for (NotificationRecipient r : links) {
                        if (r == null || r.getUserId() == null) {
                            continue;
                        }
                        Long uid = r.getUserId();
                        // 未参赛办赛人员不应留在个人向通知收件里
                        if (organizers.contains(uid) && !participants.contains(uid)) {
                            changed = true;
                            continue;
                        }
                        keep.add(uid);
                    }
                    if (changed) {
                        notificationService.replaceRecipients(m.getId(), keep);
                        personalTrimmed++;
                    }
                }
            }
            systemConfigService.setConfigValue(FLAG_KEY, "done",
                    "通知受众纠偏 v2：环节汇总含已参赛办赛人员；个人向不含未参赛办赛人员");
            log.info("通知受众回填完成(v2)：汇总类 {} 条，抽签开始/完成 {} 条，个人向裁剪 {} 条",
                    staffFixed, drawFixed, personalTrimmed);
        } catch (Exception ex) {
            log.warn("通知受众回填失败（将在下次启动重试）", ex);
        }
    }

    private Set<Long> loadRecipientUserIds(Long messageId) {
        Set<Long> ids = new HashSet<>();
        List<NotificationRecipient> links = recipientMapper.selectList(Wrappers.<NotificationRecipient>lambdaQuery()
                .eq(NotificationRecipient::getMessageId, messageId));
        if (links != null) {
            for (NotificationRecipient r : links) {
                if (r != null && r.getUserId() != null) {
                    ids.add(r.getUserId());
                }
            }
        }
        return ids;
    }

    private Long resolveTournamentId(NotificationMessage m) {
        if (m.getSourceRefId() == null) {
            return null;
        }
        String st = m.getSourceType();
        if ("T_KO_P".equals(st)) {
            Match match = matchService.getById(m.getSourceRefId());
            return match != null ? match.getTournamentId() : null;
        }
        if ("T_GE_G".equals(st)) {
            TournamentGroup g = groupMapper.selectById(m.getSourceRefId());
            return g != null ? g.getTournamentId() : null;
        }
        return m.getSourceRefId();
    }

    private static boolean isStaffSummary(String st) {
        if (st == null) {
            return false;
        }
        return st.startsWith("DRAW_STF_")
                || "T_GE_S".equals(st)
                || "T_GE_G".equals(st)
                || st.startsWith("T_KS_")
                || "T_END_S".equals(st)
                || "REGISTRATION_CLOSE".equals(st);
    }

    private static boolean isPersonal(String st) {
        if (st == null) {
            return false;
        }
        return st.startsWith("DRAW_GRP_")
                || "T_GE_P".equals(st)
                || "T_KO_P".equals(st)
                || "T_PTS_P".equals(st)
                || st.startsWith("T_DDL_");
    }
}
