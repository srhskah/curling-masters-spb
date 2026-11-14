package com.example.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.entity.NotificationMessage;
import com.example.entity.Tournament;
import com.example.mapper.NotificationMessageMapper;
import com.example.service.INotificationService;
import com.example.service.SystemConfigService;
import com.example.service.TournamentService;
import com.example.service.TournamentUrlService;
import com.example.service.impl.TournamentLifecycleNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 一次性回填：通知正文里的「查看/下载」相对链接保留 href；淘汰赛落败者按积分高→低重排；
 * 报名开启通知补上查看赛事链接。
 */
@Component
public class NotificationLinkBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NotificationLinkBackfillRunner.class);
    private static final String FLAG_KEY = "notification_link_backfill_v1";
    private static final Pattern KO_ROUND_SRC = Pattern.compile("^T_KS_(\\d+)$");

    private final SystemConfigService systemConfigService;
    private final INotificationService notificationService;
    private final NotificationMessageMapper messageMapper;
    private final TournamentLifecycleNotificationService lifecycleNotificationService;
    private final TournamentService tournamentService;
    private final TournamentUrlService tournamentUrlService;

    public NotificationLinkBackfillRunner(SystemConfigService systemConfigService,
                                          INotificationService notificationService,
                                          NotificationMessageMapper messageMapper,
                                          TournamentLifecycleNotificationService lifecycleNotificationService,
                                          TournamentService tournamentService,
                                          TournamentUrlService tournamentUrlService) {
        this.systemConfigService = systemConfigService;
        this.notificationService = notificationService;
        this.messageMapper = messageMapper;
        this.lifecycleNotificationService = lifecycleNotificationService;
        this.tournamentService = tournamentService;
        this.tournamentUrlService = tournamentUrlService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if ("done".equalsIgnoreCase(systemConfigService.getConfigValue(FLAG_KEY, ""))) {
            return;
        }
        int koFixed = 0;
        int regFixed = 0;
        try {
            List<NotificationMessage> koMsgs = messageMapper.selectList(Wrappers.<NotificationMessage>lambdaQuery()
                    .likeRight(NotificationMessage::getSourceType, "T_KS_"));
            if (koMsgs != null) {
                for (NotificationMessage m : koMsgs) {
                    if (m == null || m.getId() == null || m.getSourceType() == null || m.getSourceRefId() == null) {
                        continue;
                    }
                    Matcher matcher = KO_ROUND_SRC.matcher(m.getSourceType());
                    if (!matcher.matches()) {
                        continue;
                    }
                    Integer round = Integer.valueOf(matcher.group(1));
                    String rebuilt = lifecycleNotificationService.rebuildKnockoutRoundStaffMarkdown(
                            m.getSourceRefId(), round);
                    if (rebuilt == null || rebuilt.isBlank()) {
                        continue;
                    }
                    if (rebuilt.equals(m.getContentMarkdown())) {
                        continue;
                    }
                    notificationService.updatePublishedContent(m.getId(), rebuilt);
                    koFixed++;
                }
            }

            List<NotificationMessage> regMsgs = messageMapper.selectList(Wrappers.<NotificationMessage>lambdaQuery()
                    .eq(NotificationMessage::getSourceType, "REGISTRATION_OPEN"));
            if (regMsgs != null) {
                for (NotificationMessage m : regMsgs) {
                    if (m == null || m.getId() == null) {
                        continue;
                    }
                    String md = m.getContentMarkdown() == null ? "" : m.getContentMarkdown();
                    if (md.contains("](")) {
                        continue;
                    }
                    Long tid = m.getSourceRefId();
                    if (tid == null) {
                        continue;
                    }
                    Tournament t = tournamentService.getById(tid);
                    if (t == null) {
                        continue;
                    }
                    String link = "\n\n[查看赛事](" + tournamentUrlService.detailUrl(t) + ")\n";
                    String trimmed = md.stripTrailing();
                    notificationService.updatePublishedContent(m.getId(), trimmed + link);
                    regFixed++;
                }
            }

            int htmlFixed = notificationService.reRenderAllPublishedHtml();
            systemConfigService.setConfigValue(FLAG_KEY, "done",
                    "通知相对链接/淘汰赛落败者排序/报名查看链接回填");
            log.info("通知回填完成：淘汰赛排序 {} 条，报名链接 {} 条，HTML 重渲 {} 条",
                    koFixed, regFixed, htmlFixed);
        } catch (Exception ex) {
            log.warn("通知链接/排序回填失败（将在下次启动重试）", ex);
        }
    }
}
