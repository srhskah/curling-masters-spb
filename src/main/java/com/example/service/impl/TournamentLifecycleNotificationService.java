package com.example.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.dto.RankingEntry;
import com.example.dto.TournamentRegistrationPreviewDto;
import com.example.dto.TournamentRegistrationRowDto;
import com.example.entity.*;
import com.example.mapper.TournamentCompetitionConfigMapper;
import com.example.mapper.TournamentGroupMapper;
import com.example.mapper.TournamentGroupMemberMapper;
import com.example.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 赛事进程消息通知：参赛者个人进度 vs 未参赛办赛人员汇总。
 * 办赛人员 = 超级管理员(role=0) + 普通管理员(role=1) + 本届主办(hostUserId)。
 */
@Service
public class TournamentLifecycleNotificationService {

    private static final Logger log = LoggerFactory.getLogger(TournamentLifecycleNotificationService.class);
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final String SRC_GROUP_END_PLAYER = "T_GE_P";
    private static final String SRC_GROUP_END_STAFF = "T_GE_S";
    /** 单组完赛汇总（未参赛办赛人员）；sourceRefId = groupId */
    private static final String SRC_GROUP_ONE_STAFF = "T_GE_G";
    private static final String SRC_KO_PAIR = "T_KO_P";
    private static final String SRC_POINTS_PLAYER = "T_PTS_P";
    private static final String SRC_KO_ROUND_STAFF = "T_KS_";
    private static final String SRC_TOURNAMENT_END_STAFF = "T_END_S";
    private static final String SRC_DEADLINE_PREFIX = "T_DDL_";
    private static final String SRC_REGISTRATION_CLOSE = "REGISTRATION_CLOSE";

    @Autowired private INotificationService notificationService;
    @Autowired private TournamentService tournamentService;
    @Autowired private UserService userService;
    @Autowired private SeriesService seriesService;
    @Autowired private SeasonService seasonService;
    @Autowired private TournamentUrlService tournamentUrlService;
    @Autowired private TournamentCompetitionConfigMapper configMapper;
    @Autowired private TournamentGroupMapper groupMapper;
    @Autowired private TournamentGroupMemberMapper groupMemberMapper;
    @Autowired private IMatchService matchService;
    @Autowired private ISetScoreService setScoreService;
    @Autowired private GroupRankingCalculator groupRankingCalculator;
    @Autowired private RankingPriorityService rankingPriorityService;
    @Autowired private UserTournamentPointsService userTournamentPointsService;
    @Autowired private RankingService rankingService;
    @Autowired private TournamentRankingRosterService tournamentRankingRosterService;
    @Autowired @Lazy private ITournamentCompetitionService competitionService;
    @Autowired @Lazy private ITournamentRegistrationService registrationService;

    /** 小组赛全部锁定后：参赛者个人进度 + 未参赛办赛人员汇总。 */
    public void onGroupStageCompleted(Long tournamentId) {
        if (tournamentId == null) {
            return;
        }
        try {
            Tournament tournament = tournamentService.getById(tournamentId);
            if (tournament == null) {
                return;
            }
            TournamentCompetitionConfig cfg = configMapper.selectById(tournamentId);
            if (cfg == null) {
                return;
            }
            List<Map<String, Object>> overall = loadOverallRankingRows(tournamentId, cfg);
            if (overall.isEmpty()) {
                return;
            }
            int bracketPlayers = Math.max(0, KnockoutBracketService.playersInFirstKnockoutRound(cfg.getKnockoutStartRound()));
            int qualifyCount = cfg.getKnockoutQualifyCount() != null ? Math.max(0, cfg.getKnockoutQualifyCount()) : 0;
            Map<Long, String> nameById = loadUsernames(overall.stream()
                    .map(r -> (Long) r.get("userId"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
            String label = levelLabel(tournament);
            String detail = tournamentUrlService.detailUrl(tournament);

            Set<Long> participants = participantUserIds(tournamentId);
            for (Map<String, Object> row : overall) {
                Long uid = (Long) row.get("userId");
                if (uid == null || !participants.contains(uid)) {
                    continue;
                }
                int overallRank = intVal(row.get("overallRank"));
                String path = pathLabel(overallRank, bracketPlayers, qualifyCount);
                StringBuilder md = new StringBuilder();
                md.append("小组赛已全部结束。\n\n");
                md.append("- 所在组：").append(strVal(row.get("groupName"), "小组")).append("\n");
                md.append("- 组内名次：第 ").append(intVal(row.get("groupRank"))).append(" 名\n");
                md.append("- 小组赛总排名：第 ").append(overallRank).append(" 名\n");
                md.append("- 战绩：").append(intVal(row.get("wins"))).append("胜")
                        .append(intVal(row.get("draws"))).append("平")
                        .append(intVal(row.get("losses"))).append("负\n");
                md.append("- 小组积分：").append(intVal(row.get("points"))).append("\n");
                md.append("- 净胜：").append(intVal(row.get("net"))).append("\n");
                md.append("- 晋级情况：").append(path).append("\n\n");
                md.append("[查看赛事](").append(detail).append(")\n");
                notificationService.sendPersonalNotification(
                        "小组赛结束：" + label,
                        md.toString(),
                        SRC_GROUP_END_PLAYER,
                        tournamentId,
                        uid);
            }

            // 环节汇总：全体办赛人员（含已参赛）
            Set<Long> staffAll = organizerUserIds(tournament);
            if (!staffAll.isEmpty()) {
                StringBuilder staff = new StringBuilder();
                staff.append("小组赛已全部结束，晋级与出局名单如下。\n\n");
                Map<String, List<Map<String, Object>>> byGroup = new LinkedHashMap<>();
                for (Map<String, Object> row : overall) {
                    String g = strVal(row.get("groupName"), "小组");
                    byGroup.computeIfAbsent(g, k -> new ArrayList<>()).add(row);
                }
                for (Map.Entry<String, List<Map<String, Object>>> e : byGroup.entrySet()) {
                    staff.append("**").append(e.getKey()).append("**\n");
                    for (Map<String, Object> row : e.getValue()) {
                        Long uid = (Long) row.get("userId");
                        staff.append("- ").append(nameById.getOrDefault(uid, "选手"))
                                .append("（组内第").append(intVal(row.get("groupRank")))
                                .append("/总第").append(intVal(row.get("overallRank")))
                                .append("，").append(intVal(row.get("wins"))).append("胜")
                                .append(intVal(row.get("points"))).append("分）\n");
                    }
                    staff.append("\n");
                }
                List<String> direct = new ArrayList<>();
                List<String> qualifier = new ArrayList<>();
                List<String> out = new ArrayList<>();
                Map<Long, Integer> pointsByUser = loadTournamentPointsMap(tournamentId);
                for (Map<String, Object> row : overall) {
                    Long uid = (Long) row.get("userId");
                    if (uid == null) {
                        continue;
                    }
                    int or = intVal(row.get("overallRank"));
                    String name = nameById.getOrDefault(uid, "选手");
                    if (or >= 1 && or <= bracketPlayers) {
                        direct.add(name);
                    } else if (or > bracketPlayers && or <= bracketPlayers + qualifyCount) {
                        qualifier.add(name);
                    } else {
                        Integer pts = pointsByUser.get(uid);
                        out.add(name + (pts != null ? "（赛事积分 " + pts + "）" : ""));
                    }
                }
                staff.append("**直接晋级首轮淘汰赛**\n");
                appendNameList(staff, direct);
                staff.append("\n**需参加首轮淘汰赛资格赛**\n");
                appendNameList(staff, qualifier);
                staff.append("\n**出局（最终赛事积分）**\n");
                appendNameList(staff, out);
                staff.append("\n[查看赛事](").append(detail).append(")\n");
                notificationService.sendNotificationToUserIds(
                        "小组赛结束汇总：" + label,
                        staff.toString(),
                        SRC_GROUP_END_STAFF,
                        tournamentId,
                        staffAll);
            }
        } catch (Exception ex) {
            log.warn("小组赛结束通知失败 tournamentId={}", tournamentId, ex);
        }
    }

    /** 淘汰赛/决赛对阵双方均已确定时，通知参赛双方（办赛人员仅当本人参赛时收到）。 */
    public void onKnockoutMatchupReady(Match match) {
        if (match == null || match.getId() == null || match.getTournamentId() == null) {
            return;
        }
        Long p1 = match.getPlayer1Id();
        Long p2 = match.getPlayer2Id();
        if (p1 == null || p2 == null) {
            return;
        }
        String pc = match.getPhaseCode();
        if (pc == null) {
            return;
        }
        if (!"MAIN".equalsIgnoreCase(pc) && !"FINAL".equalsIgnoreCase(pc)
                && !"QUALIFIER".equalsIgnoreCase(pc)) {
            return;
        }
        try {
            Tournament tournament = tournamentService.getById(match.getTournamentId());
            if (tournament == null) {
                return;
            }
            Set<Long> participants = participantUserIds(match.getTournamentId());
            Map<Long, String> names = loadUsernames(List.of(p1, p2));
            String label = levelLabel(tournament);
            String roundLabel = knockoutRoundLabel(match);
            String category = match.getCategory() == null ? roundLabel : match.getCategory();
            String detail = tournamentUrlService.detailUrl(tournament) + "?section=matches";
            for (Long uid : List.of(p1, p2)) {
                if (!participants.contains(uid)) {
                    continue;
                }
                Long opp = Objects.equals(uid, p1) ? p2 : p1;
                String md = "你的对阵已确定。\n\n"
                        + "- 轮次：" + category + "\n"
                        + "- 对手：" + names.getOrDefault(opp, "待定") + "\n\n"
                        + "[查看对阵](" + detail + ")\n";
                notificationService.sendPersonalNotification(
                        "对阵通知：" + label + " " + roundLabel,
                        md,
                        SRC_KO_PAIR,
                        match.getId(),
                        uid);
            }
        } catch (Exception ex) {
            log.warn("淘汰赛对阵通知失败 matchId={}", match.getId(), ex);
        }
    }

    /** 某轮淘汰赛全部锁定后：未参赛办赛人员收到本轮落败者最终赛事积分。 */
    public void onKnockoutRoundCompleted(Long tournamentId, Integer round) {
        if (tournamentId == null || round == null) {
            return;
        }
        try {
            Tournament tournament = tournamentService.getById(tournamentId);
            if (tournament == null) {
                return;
            }
            Set<Long> staffAll = organizerUserIds(tournament);
            if (staffAll.isEmpty()) {
                return;
            }
            List<Match> matches = matchService.lambdaQuery()
                    .eq(Match::getTournamentId, tournamentId)
                    .eq(Match::getRound, round)
                    .in(Match::getPhaseCode, List.of("MAIN", "FINAL"))
                    .list();
            if (matches == null || matches.isEmpty()) {
                return;
            }
            boolean allLocked = matches.stream()
                    .allMatch(m -> Boolean.TRUE.equals(m.getResultLocked()) && m.getWinnerId() != null);
            if (!allLocked) {
                return;
            }
            Map<Long, Integer> pointsByUser = loadTournamentPointsMap(tournamentId);
            Map<Long, Integer> placements = competitionService.getProgressSettledPlacementRanks(tournamentId);
            Set<Long> loserIds = new LinkedHashSet<>();
            for (Match m : matches) {
                Long loser = loserOf(m);
                if (loser != null) {
                    loserIds.add(loser);
                }
            }
            if (loserIds.isEmpty()) {
                return;
            }
            String label = levelLabel(tournament);
            String md = buildKnockoutRoundLosersMarkdown(tournament, round, loserIds, pointsByUser, placements);
            notificationService.sendNotificationToUserIds(
                    "淘汰赛阶段结束：" + label + " " + knockoutRoundLabelForField(round),
                    md,
                    SRC_KO_ROUND_STAFF + round,
                    tournamentId,
                    staffAll);
        } catch (Exception ex) {
            log.warn("淘汰赛轮次结束通知失败 tournamentId={} round={}", tournamentId, round, ex);
        }
    }

    /** 金牌赛锁定后：未参赛办赛人员收到全量最终赛事积分。 */
    public void onTournamentFullyCompleted(Long tournamentId) {
        if (tournamentId == null) {
            return;
        }
        try {
            Tournament tournament = tournamentService.getById(tournamentId);
            if (tournament == null) {
                return;
            }
            Match gold = findGoldMatch(tournamentId);
            if (gold == null || !Boolean.TRUE.equals(gold.getResultLocked()) || gold.getWinnerId() == null) {
                return;
            }
            Set<Long> staffAll = organizerUserIds(tournament);
            if (staffAll.isEmpty()) {
                return;
            }
            Map<Long, Integer> placements = competitionService.getProgressSettledPlacementRanks(tournamentId);
            Map<Long, Integer> pointsByUser = loadTournamentPointsMap(tournamentId);
            List<Long> ordered = new ArrayList<>(pointsByUser.keySet());
            ordered.sort((a, b) -> {
                Integer pa = placements != null ? placements.get(a) : null;
                Integer pb = placements != null ? placements.get(b) : null;
                if (pa != null && pb != null && !pa.equals(pb)) {
                    return Integer.compare(pa, pb);
                }
                if (pa != null && pb == null) {
                    return -1;
                }
                if (pa == null && pb != null) {
                    return 1;
                }
                return Integer.compare(pointsByUser.getOrDefault(b, 0), pointsByUser.getOrDefault(a, 0));
            });
            Map<Long, String> names = loadUsernames(ordered);
            String label = levelLabel(tournament);
            StringBuilder md = new StringBuilder();
            md.append("本届赛事已全部结束，最终赛事积分如下：\n\n");
            int i = 1;
            for (Long uid : ordered) {
                Integer place = placements != null ? placements.get(uid) : null;
                md.append(i++).append(". ").append(names.getOrDefault(uid, "选手"));
                if (place != null) {
                    md.append(" — 第 ").append(place).append(" 名");
                }
                md.append("，积分 ").append(pointsByUser.getOrDefault(uid, 0)).append("\n");
            }
            md.append("\n[查看赛事](").append(tournamentUrlService.detailUrl(tournament)).append(")\n");
            notificationService.sendNotificationToUserIds(
                    "赛事结束·最终积分：" + label,
                    md.toString(),
                    SRC_TOURNAMENT_END_STAFF,
                    tournamentId,
                    staffAll);
        } catch (Exception ex) {
            log.warn("赛事结束通知失败 tournamentId={}", tournamentId, ex);
        }
    }

    /** 参赛者获得/锁定赛事积分后：通知本人积分及最新赛季/总排名。 */
    public void notifyPlayerPointsIfSettled(Long tournamentId, Long userId) {
        if (tournamentId == null || userId == null) {
            return;
        }
        try {
            Set<Long> participants = participantUserIds(tournamentId);
            if (!participants.contains(userId)) {
                return;
            }
            Integer placement = competitionService.getProgressSettledPlacementRank(tournamentId, userId);
            if (placement == null || placement <= 0) {
                return;
            }
            UserTournamentPoints utp = userTournamentPointsService.lambdaQuery()
                    .eq(UserTournamentPoints::getTournamentId, tournamentId)
                    .eq(UserTournamentPoints::getUserId, userId)
                    .last("LIMIT 1")
                    .one();
            if (utp == null || utp.getPoints() == null) {
                return;
            }
            Tournament tournament = tournamentService.getById(tournamentId);
            if (tournament == null) {
                return;
            }
            Integer seasonRank = resolveSeasonRank(tournament, userId);
            Integer totalRank = resolveTotalRank(userId);
            String label = levelLabel(tournament);
            StringBuilder md = new StringBuilder();
            md.append("你在本届赛事的成绩已计入积分。\n\n");
            md.append("- 本届名次：第 ").append(placement).append(" 名\n");
            md.append("- 本届赛事积分：").append(utp.getPoints()).append("\n");
            if (seasonRank != null) {
                md.append("- 最新赛季排名：第 ").append(seasonRank).append(" 名\n");
            }
            if (totalRank != null) {
                md.append("- 最新总排名：第 ").append(totalRank).append(" 名\n");
            }
            md.append("\n[查看赛事](").append(tournamentUrlService.detailUrl(tournament)).append(")\n");
            notificationService.sendPersonalNotification(
                    "赛事积分：" + label,
                    md.toString(),
                    SRC_POINTS_PLAYER,
                    tournamentId,
                    userId);
        } catch (Exception ex) {
            log.warn("赛事积分通知失败 tournamentId={} userId={}", tournamentId, userId, ex);
        }
    }

    public void afterPointsRecomputed(Long tournamentId) {
        if (tournamentId == null) {
            return;
        }
        try {
            Map<Long, Integer> placements = competitionService.getProgressSettledPlacementRanks(tournamentId);
            if (placements == null || placements.isEmpty()) {
                return;
            }
            for (Long uid : placements.keySet()) {
                notifyPlayerPointsIfSettled(tournamentId, uid);
            }
        } catch (Exception ex) {
            log.warn("积分重算后通知失败 tournamentId={}", tournamentId, ex);
        }
    }

    /** 验收后钩子：单组完赛 / 小组结束 / 轮次结束 / 赛事结束 / 积分通知。 */
    public void afterMatchLocked(Match match) {
        if (match == null || match.getTournamentId() == null) {
            return;
        }
        Long tid = match.getTournamentId();
        try {
            if ("GROUP".equalsIgnoreCase(match.getPhaseCode()) && match.getGroupId() != null
                    && isSingleGroupFullyLocked(tid, match.getGroupId())) {
                onSingleGroupCompleted(tid, match.getGroupId());
            }
            if ("GROUP".equalsIgnoreCase(match.getPhaseCode()) && isGroupStageFullyLocked(tid)) {
                onGroupStageCompleted(tid);
            }
            if (("MAIN".equalsIgnoreCase(match.getPhaseCode()) || "FINAL".equalsIgnoreCase(match.getPhaseCode()))
                    && match.getRound() != null
                    && isKnockoutRoundFullyLocked(tid, match.getRound())) {
                onKnockoutRoundCompleted(tid, match.getRound());
            }
            if (isGoldMatch(match) && Boolean.TRUE.equals(match.getResultLocked())) {
                onTournamentFullyCompleted(tid);
            }
            afterPointsRecomputed(tid);
        } catch (Exception ex) {
            log.warn("验收后生命周期通知失败 matchId={}", match.getId(), ex);
        }
    }

    /** 某小组全部场次锁定后：未参赛办赛人员收到该组详细数据。 */
    public void onSingleGroupCompleted(Long tournamentId, Long groupId) {
        if (tournamentId == null || groupId == null) {
            return;
        }
        try {
            Tournament tournament = tournamentService.getById(tournamentId);
            TournamentGroup group = groupMapper.selectById(groupId);
            if (tournament == null || group == null) {
                return;
            }
            Set<Long> staffAll = organizerUserIds(tournament);
            if (staffAll.isEmpty()) {
                return;
            }
            TournamentCompetitionConfig cfg = configMapper.selectById(tournamentId);
            List<Map<String, Object>> overall = loadOverallRankingRows(tournamentId, cfg);
            String groupName = group.getGroupName() != null && !group.getGroupName().isBlank()
                    ? group.getGroupName() : "小组";
            Set<Long> memberIds = groupMemberMapper.selectList(Wrappers.<TournamentGroupMember>lambdaQuery()
                            .eq(TournamentGroupMember::getTournamentId, tournamentId)
                            .eq(TournamentGroupMember::getGroupId, groupId))
                    .stream().map(TournamentGroupMember::getUserId).filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            List<Map<String, Object>> rows = overall.stream()
                    .filter(r -> memberIds.contains((Long) r.get("userId")))
                    .toList();
            Map<Long, String> names = loadUsernames(rows.stream()
                    .map(r -> (Long) r.get("userId")).filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
            String label = levelLabel(tournament);
            StringBuilder md = new StringBuilder();
            md.append("**").append(groupName).append("** 小组赛已全部结束。\n\n");
            for (Map<String, Object> row : rows) {
                Long uid = (Long) row.get("userId");
                md.append("- ").append(names.getOrDefault(uid, "选手"))
                        .append("：组内第 ").append(intVal(row.get("groupRank")))
                        .append(" 名 / 总第 ").append(intVal(row.get("overallRank")))
                        .append(" 名，").append(intVal(row.get("wins"))).append("胜")
                        .append(intVal(row.get("draws"))).append("平")
                        .append(intVal(row.get("losses"))).append("负，")
                        .append(intVal(row.get("points"))).append(" 分，净胜 ")
                        .append(intVal(row.get("net"))).append("\n");
            }
            md.append("\n[查看赛事](").append(tournamentUrlService.detailUrl(tournament)).append(")\n");
            notificationService.sendNotificationToUserIds(
                    "小组完赛：" + label + " " + groupName,
                    md.toString(),
                    SRC_GROUP_ONE_STAFF,
                    groupId,
                    staffAll);
        } catch (Exception ex) {
            log.warn("单组完赛通知失败 tournamentId={} groupId={}", tournamentId, groupId, ex);
        }
    }

    /** 报名截止后：未参赛办赛人员收到报名结束与结果汇总。 */
    public void onRegistrationClosed(Long tournamentId) {
        if (tournamentId == null) {
            return;
        }
        try {
            Tournament tournament = tournamentService.getById(tournamentId);
            if (tournament == null) {
                return;
            }
            TournamentRegistrationSetting setting = registrationService.getSetting(tournamentId);
            if (setting == null || setting.getDeadline() == null) {
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            if (now.isBefore(setting.getDeadline())) {
                return;
            }
            List<TournamentRegistrationRowDto> rows = registrationService.listRows(tournamentId, now);
            Set<Long> staffAll = organizerUserIds(tournament);
            if (staffAll.isEmpty()) {
                return;
            }
            TournamentRegistrationPreviewDto preview = registrationService.preview(tournamentId, now);
            String label = levelLabel(tournament);
            StringBuilder md = new StringBuilder();
            md.append("报名已截止。\n\n");
            if (preview != null && preview.getModeDescription() != null) {
                md.append(preview.getModeDescription()).append("\n\n");
            }
            md.append("**正赛直通**\n");
            appendNameList(md, preview != null ? preview.getMainDirectUsernames() : null);
            md.append("\n**资格赛种子**\n");
            appendNameList(md, preview != null ? preview.getQualifierSeedUsernames() : null);
            int pending = 0;
            int rejected = 0;
            if (rows != null) {
                for (TournamentRegistrationRowDto r : rows) {
                    if (r == null || r.getStatus() == null) {
                        continue;
                    }
                    if (r.getStatus() == 0 && !r.isEffectiveApproved()) {
                        pending++;
                    } else if (r.getStatus() == 2) {
                        rejected++;
                    }
                }
            }
            md.append("\n待审未默认通过：").append(pending).append("；拒绝：").append(rejected).append("\n");
            md.append("\n[查看赛事](").append(tournamentUrlService.detailUrl(tournament)).append(")\n");
            notificationService.sendNotificationToUserIds(
                    "报名结束：" + label,
                    md.toString(),
                    SRC_REGISTRATION_CLOSE,
                    tournamentId,
                    staffAll);
        } catch (Exception ex) {
            log.warn("报名截止通知失败 tournamentId={}", tournamentId, ex);
        }
    }

    /** 每 5 分钟：报名已截止且尚未发过办赛汇总时补发（先廉价查重，避免每分钟 listRows/preview）。 */
    @Scheduled(cron = "0 */5 * * * ?")
    public void scanRegistrationClosedNotices() {
        LocalDateTime now = LocalDateTime.now();
        List<Tournament> tournaments;
        try {
            tournaments = tournamentService.lambdaQuery()
                    .in(Tournament::getStatus, List.of(0, 1))
                    .list();
        } catch (Exception ex) {
            log.warn("报名截止扫描失败", ex);
            return;
        }
        if (tournaments == null || tournaments.isEmpty()) {
            return;
        }
        for (Tournament t : tournaments) {
            if (t == null || t.getId() == null) {
                continue;
            }
            try {
                if (notificationService.hasPublishedBySource(SRC_REGISTRATION_CLOSE, t.getId())) {
                    continue;
                }
                TournamentRegistrationSetting s = registrationService.getSetting(t.getId());
                if (s == null || !Boolean.TRUE.equals(s.getEnabled()) || s.getDeadline() == null) {
                    continue;
                }
                if (now.isBefore(s.getDeadline())) {
                    continue;
                }
                onRegistrationClosed(t.getId());
            } catch (Exception ex) {
                log.warn("报名截止通知扫描失败 tournamentId={}", t.getId(), ex);
            }
        }
    }

    /** 截止前 24 小时提醒（仅参赛者；办赛人员仅当本人参赛时收到）。每 15 分钟扫描。 */
    @Scheduled(cron = "0 */15 * * * ?")
    public void scanDeadlineReminders() {
        LocalDateTime now = LocalDateTime.now();
        List<TournamentCompetitionConfig> configs;
        try {
            configs = configMapper.selectList(Wrappers.lambdaQuery());
        } catch (Exception ex) {
            log.warn("截止提醒扫描失败", ex);
            return;
        }
        if (configs == null) {
            return;
        }
        for (TournamentCompetitionConfig cfg : configs) {
            if (cfg == null || cfg.getTournamentId() == null) {
                continue;
            }
            try {
                remindIfDue(cfg, "G", "小组赛", cfg.getGroupStageDeadline(), now);
                remindIfDue(cfg, "Q", "首轮淘汰赛资格赛", cfg.getKnockoutQualifierDeadline(), now);
                for (int round : TournamentCompetitionConfig.knockoutRoundsFromStart(cfg.getKnockoutStartRound())) {
                    remindIfDue(cfg, String.valueOf(round), knockoutRoundLabelForField(round),
                            cfg.getKnockoutDeadlineForRound(round), now);
                }
            } catch (Exception ex) {
                log.warn("截止提醒处理失败 tournamentId={}", cfg.getTournamentId(), ex);
            }
        }
    }

    private void remindIfDue(TournamentCompetitionConfig cfg, String key, String stageLabel,
                             LocalDateTime deadline, LocalDateTime now) {
        if (deadline == null) {
            return;
        }
        LocalDateTime windowStart = deadline.minusHours(24);
        if (now.isBefore(windowStart) || !now.isBefore(deadline)) {
            return;
        }
        Long tid = cfg.getTournamentId();
        Tournament tournament = tournamentService.getById(tid);
        if (tournament == null) {
            return;
        }
        Set<Long> participants = participantUserIds(tid);
        if (participants.isEmpty()) {
            return;
        }
        String label = levelLabel(tournament);
        String md = stageLabel + "将于 **" + DT.format(deadline) + "** 截止（距截止不足 24 小时），请尽快完成比赛与验收。\n\n"
                + "[查看赛事](" + tournamentUrlService.detailUrl(tournament) + ")\n";
        String sourceType = SRC_DEADLINE_PREFIX + key;
        // 每人一条，避免未参赛办赛人员误收；dedup 靠 personal
        for (Long uid : participants) {
            notificationService.sendPersonalNotification(
                    "截止提醒：" + label + " " + stageLabel,
                    md,
                    sourceType,
                    tid,
                    uid);
        }
    }

    public Set<Long> participantUserIds(Long tournamentId) {
        Set<Long> ids = new LinkedHashSet<>();
        if (tournamentId == null) {
            return ids;
        }
        Set<Long> roster = tournamentRankingRosterService.rosterUserIdsForEventRanking(tournamentId);
        if (roster != null) {
            ids.addAll(roster);
        }
        List<TournamentGroupMember> members = groupMemberMapper.selectList(
                Wrappers.<TournamentGroupMember>lambdaQuery()
                        .eq(TournamentGroupMember::getTournamentId, tournamentId));
        if (members != null) {
            for (TournamentGroupMember m : members) {
                if (m != null && m.getUserId() != null) {
                    ids.add(m.getUserId());
                }
            }
        }
        return ids;
    }

    public Set<Long> organizerUserIds(Tournament tournament) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        List<User> admins = userService.lambdaQuery().in(User::getRole, List.of(0, 1)).list();
        if (admins != null) {
            for (User u : admins) {
                if (u != null && u.getId() != null) {
                    ids.add(u.getId());
                }
            }
        }
        if (tournament != null && tournament.getHostUserId() != null) {
            ids.add(tournament.getHostUserId());
        }
        return ids;
    }

    public Set<Long> staffUserIdsExcludingParticipants(Tournament tournament, Set<Long> participants) {
        Set<Long> staff = organizerUserIds(tournament);
        if (participants != null) {
            staff.removeAll(participants);
        }
        return staff;
    }

    private List<Map<String, Object>> loadOverallRankingRows(Long tournamentId, TournamentCompetitionConfig cfg) {
        List<TournamentGroup> groups = groupMapper.selectList(Wrappers.<TournamentGroup>lambdaQuery()
                .eq(TournamentGroup::getTournamentId, tournamentId)
                .orderByAsc(TournamentGroup::getGroupOrder));
        if (groups == null || groups.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Long>> memberIdsByGroup = new LinkedHashMap<>();
        for (TournamentGroup g : groups) {
            List<Long> uids = groupMemberMapper.selectList(Wrappers.<TournamentGroupMember>lambdaQuery()
                            .eq(TournamentGroupMember::getTournamentId, tournamentId)
                            .eq(TournamentGroupMember::getGroupId, g.getId()))
                    .stream().map(TournamentGroupMember::getUserId).filter(Objects::nonNull).toList();
            memberIdsByGroup.put(g.getId(), uids);
        }
        Map<Long, String> uname = userService.list().stream()
                .filter(Objects::nonNull)
                .filter(u -> u.getId() != null)
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
        List<Match> matches = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tournamentId)
                .eq(Match::getPhaseCode, "GROUP")
                .list();
        List<Long> mids = matches.stream().map(Match::getId).filter(Objects::nonNull).toList();
        Map<Long, List<SetScore>> scoreByMatch = mids.isEmpty() ? Map.of() : setScoreService.lambdaQuery()
                .in(SetScore::getMatchId, mids)
                .list()
                .stream()
                .collect(Collectors.groupingBy(SetScore::getMatchId));
        var rankingPriority = rankingPriorityService.criteriaOrderForTournament(tournamentId);
        Map<Long, List<Map<String, Object>>> byG = groupRankingCalculator.buildGroupRankingsByMemberIds(
                groups, memberIdsByGroup, uname, matches, scoreByMatch, cfg, rankingPriority);
        boolean allowDraw = cfg.getGroupAllowDraw() == null || !Boolean.FALSE.equals(cfg.getGroupAllowDraw());
        int regularSets = (cfg.getGroupStageSets() != null && cfg.getGroupStageSets() > 0) ? cfg.getGroupStageSets() : 8;
        groupRankingCalculator.buildPseudoGroupExportRowsAndApplyMainRanks(
                groups, byG, matches, scoreByMatch, Map.of(), uname, allowDraw, regularSets, rankingPriority);
        return groupRankingCalculator.buildOverallRanking(byG, rankingPriority);
    }

    private Map<Long, Integer> loadTournamentPointsMap(Long tournamentId) {
        Map<Long, Integer> map = new HashMap<>();
        List<UserTournamentPoints> rows = userTournamentPointsService.lambdaQuery()
                .eq(UserTournamentPoints::getTournamentId, tournamentId)
                .list();
        if (rows == null) {
            return map;
        }
        for (UserTournamentPoints r : rows) {
            if (r != null && r.getUserId() != null && r.getPoints() != null) {
                map.put(r.getUserId(), r.getPoints());
            }
        }
        return map;
    }

    private Map<Long, String> loadUsernames(Collection<Long> userIds) {
        Map<Long, String> map = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return map;
        }
        List<User> users = userService.listByIds(userIds.stream().filter(Objects::nonNull).distinct().toList());
        if (users == null) {
            return map;
        }
        for (User u : users) {
            if (u != null && u.getId() != null) {
                map.put(u.getId(), u.getUsername() != null ? u.getUsername() : ("用户" + u.getId()));
            }
        }
        return map;
    }

    private Integer resolveSeasonRank(Tournament tournament, Long userId) {
        if (tournament == null || tournament.getSeriesId() == null || userId == null) {
            return null;
        }
        Series series = seriesService.getById(tournament.getSeriesId());
        if (series == null || series.getSeasonId() == null) {
            return null;
        }
        List<RankingEntry> season = rankingService.getSeasonRanking(series.getSeasonId(), null);
        return rankOf(season, userId);
    }

    private Integer resolveTotalRank(Long userId) {
        return rankOf(rankingService.getTotalRanking(null), userId);
    }

    private static Integer rankOf(List<RankingEntry> entries, Long userId) {
        if (entries == null || userId == null) {
            return null;
        }
        for (int i = 0; i < entries.size(); i++) {
            RankingEntry e = entries.get(i);
            if (e != null && userId.equals(e.getUserId())) {
                return i + 1;
            }
        }
        return null;
    }

    private boolean isGroupStageFullyLocked(Long tournamentId) {
        long total = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tournamentId)
                .eq(Match::getPhaseCode, "GROUP")
                .count();
        if (total == 0) {
            return false;
        }
        long locked = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tournamentId)
                .eq(Match::getPhaseCode, "GROUP")
                .eq(Match::getResultLocked, true)
                .count();
        return total == locked;
    }

    private boolean isSingleGroupFullyLocked(Long tournamentId, Long groupId) {
        if (tournamentId == null || groupId == null) {
            return false;
        }
        long total = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tournamentId)
                .eq(Match::getPhaseCode, "GROUP")
                .eq(Match::getGroupId, groupId)
                .count();
        if (total == 0) {
            return false;
        }
        long locked = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tournamentId)
                .eq(Match::getPhaseCode, "GROUP")
                .eq(Match::getGroupId, groupId)
                .eq(Match::getResultLocked, true)
                .count();
        return total == locked;
    }

    private boolean isKnockoutRoundFullyLocked(Long tournamentId, Integer round) {
        List<Match> oneRound = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tournamentId)
                .eq(Match::getRound, round)
                .in(Match::getPhaseCode, List.of("MAIN", "FINAL"))
                .list();
        if (oneRound == null || oneRound.isEmpty()) {
            return false;
        }
        return oneRound.stream().allMatch(m -> Boolean.TRUE.equals(m.getResultLocked()) && m.getWinnerId() != null);
    }

    private Match findGoldMatch(Long tournamentId) {
        return matchService.lambdaQuery()
                .eq(Match::getTournamentId, tournamentId)
                .eq(Match::getRound, 1)
                .and(w -> w.eq(Match::getPhaseCode, "FINAL").or().like(Match::getCategory, "金牌"))
                .last("LIMIT 1")
                .one();
    }

    private static boolean isGoldMatch(Match m) {
        if (m == null) {
            return false;
        }
        if ("FINAL".equalsIgnoreCase(m.getPhaseCode()) && m.getCategory() != null && m.getCategory().contains("金牌")) {
            return true;
        }
        return m.getCategory() != null && m.getCategory().contains("金牌");
    }

    private static Long loserOf(Match m) {
        if (m == null || m.getWinnerId() == null) {
            return null;
        }
        if (Objects.equals(m.getWinnerId(), m.getPlayer1Id())) {
            return m.getPlayer2Id();
        }
        if (Objects.equals(m.getWinnerId(), m.getPlayer2Id())) {
            return m.getPlayer1Id();
        }
        return null;
    }

    /**
     * 落败者按赛事积分从高到低；同分按名次升序。
     */
    String buildKnockoutRoundLosersMarkdown(Tournament tournament,
                                           Integer round,
                                           Collection<Long> loserIds,
                                           Map<Long, Integer> pointsByUser,
                                           Map<Long, Integer> placements) {
        List<Long> ordered = new ArrayList<>(loserIds);
        ordered.sort((a, b) -> {
            int ptsA = pointsByUser != null ? pointsByUser.getOrDefault(a, 0) : 0;
            int ptsB = pointsByUser != null ? pointsByUser.getOrDefault(b, 0) : 0;
            if (ptsA != ptsB) {
                return Integer.compare(ptsB, ptsA);
            }
            Integer pa = placements != null ? placements.get(a) : null;
            Integer pb = placements != null ? placements.get(b) : null;
            if (pa != null && pb != null && !pa.equals(pb)) {
                return Integer.compare(pa, pb);
            }
            if (pa != null && pb == null) {
                return -1;
            }
            if (pa == null && pb != null) {
                return 1;
            }
            return Long.compare(a, b);
        });
        Map<Long, String> names = loadUsernames(ordered);
        StringBuilder md = new StringBuilder();
        md.append(knockoutRoundLabelForField(round)).append("已全部结束，落败者最终赛事积分：\n\n");
        for (Long uid : ordered) {
            md.append("- ").append(names.getOrDefault(uid, "选手"));
            Integer place = placements != null ? placements.get(uid) : null;
            Integer pts = pointsByUser != null ? pointsByUser.get(uid) : null;
            if (place != null) {
                md.append("（第 ").append(place).append(" 名");
                if (pts != null) {
                    md.append("，积分 ").append(pts);
                }
                md.append("）");
            } else if (pts != null) {
                md.append("（积分 ").append(pts).append("）");
            }
            md.append("\n");
        }
        md.append("\n[查看赛事](").append(tournamentUrlService.detailUrl(tournament)).append(")\n");
        return md.toString();
    }

    /**
     * 按当前赛况重算某条淘汰赛轮次结束通知正文（积分高→低），供历史数据回填。
     */
    public String rebuildKnockoutRoundStaffMarkdown(Long tournamentId, Integer round) {
        if (tournamentId == null || round == null) {
            return null;
        }
        Tournament tournament = tournamentService.getById(tournamentId);
        if (tournament == null) {
            return null;
        }
        List<Match> matches = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tournamentId)
                .eq(Match::getRound, round)
                .in(Match::getPhaseCode, List.of("MAIN", "FINAL"))
                .list();
        if (matches == null || matches.isEmpty()) {
            return null;
        }
        Set<Long> loserIds = new LinkedHashSet<>();
        for (Match m : matches) {
            Long loser = loserOf(m);
            if (loser != null) {
                loserIds.add(loser);
            }
        }
        if (loserIds.isEmpty()) {
            return null;
        }
        return buildKnockoutRoundLosersMarkdown(
                tournament,
                round,
                loserIds,
                loadTournamentPointsMap(tournamentId),
                competitionService.getProgressSettledPlacementRanks(tournamentId));
    }

    private static String pathLabel(int overallRank, int bracketPlayers, int qualifyCount) {
        if (overallRank >= 1 && overallRank <= bracketPlayers) {
            return "直接晋级首轮淘汰赛";
        }
        if (overallRank > bracketPlayers && overallRank <= bracketPlayers + qualifyCount) {
            return "需参加首轮淘汰赛资格赛";
        }
        return "出局";
    }

    private static String knockoutRoundLabel(Match m) {
        if (m == null) {
            return "淘汰赛";
        }
        if (m.getCategory() != null && m.getCategory().contains("铜牌")) {
            return "铜牌赛";
        }
        if (m.getCategory() != null && m.getCategory().contains("金牌")) {
            return "决赛";
        }
        if ("QUALIFIER".equalsIgnoreCase(m.getPhaseCode())) {
            return "资格赛";
        }
        return knockoutRoundLabelForField(m.getRound());
    }

    private static String knockoutRoundLabelForField(Integer round) {
        if (round == null) {
            return "淘汰赛";
        }
        return switch (round) {
            case 16 -> "1/16决赛";
            case 8 -> "1/8决赛";
            case 4 -> "1/4决赛";
            case 2 -> "半决赛";
            case 1 -> "决赛";
            default -> "淘汰赛第" + round + "轮";
        };
    }

    private static String levelLabel(Tournament t) {
        if (t != null && t.getLevelCode() != null && !t.getLevelCode().isBlank()) {
            return t.getLevelCode();
        }
        return t != null && t.getId() != null ? ("赛事#" + t.getId()) : "赛事";
    }

    private static int intVal(Object o) {
        return o instanceof Number ? ((Number) o).intValue() : 0;
    }

    private static String strVal(Object o, String def) {
        if (o == null) {
            return def;
        }
        String s = String.valueOf(o);
        return s.isBlank() ? def : s;
    }

    private static void appendNameList(StringBuilder sb, List<String> names) {
        if (names == null || names.isEmpty()) {
            sb.append("- （无）\n");
            return;
        }
        for (String n : names) {
            sb.append("- ").append(n).append("\n");
        }
    }
}
