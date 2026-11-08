package com.example.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.dto.ViewerTournamentProgress;
import com.example.entity.Match;
import com.example.entity.SetScore;
import com.example.entity.Tournament;
import com.example.entity.TournamentEntry;
import com.example.entity.TournamentGroup;
import com.example.entity.TournamentGroupMember;
import com.example.entity.TournamentRegistration;
import com.example.entity.User;
import com.example.mapper.TournamentEntryMapper;
import com.example.mapper.TournamentGroupMapper;
import com.example.mapper.TournamentGroupMemberMapper;
import com.example.mapper.TournamentRegistrationMapper;
import com.example.service.IMatchService;
import com.example.service.ISetScoreService;
import com.example.service.TournamentService;
import com.example.service.TournamentUrlService;
import com.example.service.UserService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 首页、赛季详情、赛事详情共用：当前用户是否参加某赛事，以及当前阶段里还没打完的场次。
 */
@Service
public class ViewerTournamentProgressService {

    private static final String KO_QUALIFIER_SOURCE = KnockoutBracketService.SOURCE_AUTO_FROM_GROUP_KO_QUALIFIER;

    private final IMatchService matchService;
    private final ISetScoreService setScoreService;
    private final TournamentGroupMemberMapper groupMemberMapper;
    private final TournamentGroupMapper groupMapper;
    private final TournamentRegistrationMapper registrationMapper;
    private final TournamentEntryMapper entryMapper;
    private final UserService userService;
    private final TournamentService tournamentService;
    private final TournamentUrlService tournamentUrlService;

    public ViewerTournamentProgressService(IMatchService matchService,
                                           ISetScoreService setScoreService,
                                           TournamentGroupMemberMapper groupMemberMapper,
                                           TournamentGroupMapper groupMapper,
                                           TournamentRegistrationMapper registrationMapper,
                                           TournamentEntryMapper entryMapper,
                                           UserService userService,
                                           TournamentService tournamentService,
                                           TournamentUrlService tournamentUrlService) {
        this.matchService = matchService;
        this.setScoreService = setScoreService;
        this.groupMemberMapper = groupMemberMapper;
        this.groupMapper = groupMapper;
        this.registrationMapper = registrationMapper;
        this.entryMapper = entryMapper;
        this.userService = userService;
        this.tournamentService = tournamentService;
        this.tournamentUrlService = tournamentUrlService;
    }

    public Map<Long, ViewerTournamentProgress> forUser(Long userId, Collection<Long> tournamentIds) {
        if (userId == null || tournamentIds == null || tournamentIds.isEmpty()) {
            return Map.of();
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (Long id : tournamentIds) {
            if (id != null) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }

        List<Match> matches = matchService.lambdaQuery()
                .in(Match::getTournamentId, ids)
                .and(w -> w.eq(Match::getPlayer1Id, userId)
                        .or().eq(Match::getPlayer2Id, userId)
                        .or().eq(Match::getHomeUserId, userId)
                        .or().eq(Match::getAwayUserId, userId))
                .list();
        List<Long> matchIds = new ArrayList<>();
        for (Match m : matches) {
            if (m != null && m.getId() != null) {
                matchIds.add(m.getId());
            }
        }
        Map<Long, List<SetScore>> scoresByMatch = matchIds.isEmpty() ? Map.of() : setScoreService.lambdaQuery()
                .in(SetScore::getMatchId, matchIds)
                .list()
                .stream()
                .filter(s -> s != null && s.getMatchId() != null)
                .collect(java.util.stream.Collectors.groupingBy(SetScore::getMatchId));

        List<TournamentGroupMember> memberships = groupMemberMapper.selectList(
                Wrappers.<TournamentGroupMember>lambdaQuery()
                        .in(TournamentGroupMember::getTournamentId, ids)
                        .eq(TournamentGroupMember::getUserId, userId));
        List<TournamentRegistration> registrations = registrationMapper.selectList(
                Wrappers.<TournamentRegistration>lambdaQuery()
                        .in(TournamentRegistration::getTournamentId, ids)
                        .eq(TournamentRegistration::getUserId, userId));
        List<TournamentEntry> entries = entryMapper.selectList(
                Wrappers.<TournamentEntry>lambdaQuery()
                        .in(TournamentEntry::getTournamentId, ids)
                        .eq(TournamentEntry::getUserId, userId));

        Set<Long> groupIds = new LinkedHashSet<>();
        for (TournamentGroupMember mem : memberships) {
            if (mem != null && mem.getGroupId() != null) {
                groupIds.add(mem.getGroupId());
            }
        }
        for (Match m : matches) {
            if (m != null && m.getGroupId() != null) {
                groupIds.add(m.getGroupId());
            }
        }
        Map<Long, String> groupNameById = new HashMap<>();
        if (!groupIds.isEmpty()) {
            List<TournamentGroup> groups = groupMapper.selectBatchIds(groupIds);
            if (groups != null) {
                for (TournamentGroup g : groups) {
                    if (g != null && g.getId() != null) {
                        groupNameById.put(g.getId(), g.getGroupName() == null || g.getGroupName().isBlank() ? "小组" : g.getGroupName());
                    }
                }
            }
        }

        Set<Long> opponentIds = new LinkedHashSet<>();
        for (Match m : matches) {
            Long opp = opponentId(m, userId);
            if (opp != null) {
                opponentIds.add(opp);
            }
        }
        Map<Long, String> nameById = new HashMap<>();
        if (!opponentIds.isEmpty()) {
            List<User> users = userService.listByIds(opponentIds);
            if (users != null) {
                for (User u : users) {
                    if (u != null && u.getId() != null && u.getUsername() != null) {
                        nameById.put(u.getId(), u.getUsername());
                    }
                }
            }
        }

        Map<Long, List<Match>> matchesByTournament = new HashMap<>();
        for (Match m : matches) {
            if (m == null || m.getTournamentId() == null) {
                continue;
            }
            matchesByTournament.computeIfAbsent(m.getTournamentId(), k -> new ArrayList<>()).add(m);
        }
        Set<Long> memberTournaments = new LinkedHashSet<>();
        Map<Long, Long> memberGroupByTournament = new HashMap<>();
        for (TournamentGroupMember mem : memberships) {
            if (mem == null || mem.getTournamentId() == null) {
                continue;
            }
            memberTournaments.add(mem.getTournamentId());
            if (mem.getGroupId() != null) {
                memberGroupByTournament.putIfAbsent(mem.getTournamentId(), mem.getGroupId());
            }
        }
        Set<Long> signedUp = new LinkedHashSet<>();
        for (TournamentRegistration r : registrations) {
            if (r != null && r.getTournamentId() != null) {
                signedUp.add(r.getTournamentId());
            }
        }
        for (TournamentEntry e : entries) {
            if (e != null && e.getTournamentId() != null) {
                signedUp.add(e.getTournamentId());
            }
        }

        Map<Long, Tournament> tournamentById = new HashMap<>();
        List<Tournament> tournaments = tournamentService.listByIds(ids);
        if (tournaments != null) {
            for (Tournament t : tournaments) {
                if (t != null && t.getId() != null) {
                    tournamentById.put(t.getId(), t);
                }
            }
        }

        Map<Long, ViewerTournamentProgress> out = new LinkedHashMap<>();
        for (Long tid : ids) {
            boolean inPlay = memberTournaments.contains(tid) || matchesByTournament.containsKey(tid);
            boolean joined = inPlay || signedUp.contains(tid);
            if (!joined) {
                continue;
            }
            ViewerTournamentProgress progress = buildOne(
                    userId,
                    tournamentById.get(tid),
                    matchesByTournament.getOrDefault(tid, List.of()),
                    scoresByMatch,
                    memberGroupByTournament.get(tid),
                    groupNameById,
                    nameById,
                    inPlay);
            out.put(tid, progress);
        }
        return out;
    }

    private ViewerTournamentProgress buildOne(Long userId,
                                              Tournament tournament,
                                              List<Match> matches,
                                              Map<Long, List<SetScore>> scoresByMatch,
                                              Long memberGroupId,
                                              Map<Long, String> groupNameById,
                                              Map<Long, String> nameById,
                                              boolean inPlay) {
        ViewerTournamentProgress progress = new ViewerTournamentProgress();
        progress.setParticipating(true);
        String base = tournamentUrlService.detailUrl(tournament);
        Map<String, Bucket> buckets = new LinkedHashMap<>();
        for (Match m : matches) {
            if (m == null || isWithdrawn(m)) {
                continue;
            }
            String key = bucketKey(m);
            Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(m, groupNameById));
            bucket.matches.add(m);
            if (!isCompleted(m, scoresByMatch)) {
                bucket.pending = true;
            }
        }
        Bucket chosen = choose(buckets.values());
        if (chosen == null && memberGroupId != null) {
            String groupName = groupNameById.getOrDefault(memberGroupId, "小组");
            progress.setStageLabel(groupName);
            progress.setMatchesUrl(base + "?section=matches&groupId=" + memberGroupId);
            return progress;
        }
        if (chosen == null) {
            progress.setStageLabel(inPlay ? "已参赛" : "已报名");
            progress.setMatchesUrl(base);
            return progress;
        }
        progress.setStageLabel(chosen.label);
        if (chosen.groupId != null) {
            progress.setMatchesUrl(base + "?section=matches&groupId=" + chosen.groupId);
        } else if (chosen.tabId != null) {
            progress.setMatchesUrl(base + "?section=matches&tab=" + chosen.tabId);
        } else {
            progress.setMatchesUrl(base + "?section=matches");
        }
        for (Match m : chosen.matches) {
            if (isCompleted(m, scoresByMatch)) {
                continue;
            }
            Long opp = opponentId(m, userId);
            String name = opp == null ? "待定" : nameById.getOrDefault(opp, "待定");
            String cat = m.getCategory() == null || m.getCategory().isBlank() ? "" : m.getCategory().trim() + " ";
            progress.getPendingOpponents().add(cat + "vs " + name);
        }
        return progress;
    }

    private static Bucket choose(Collection<Bucket> buckets) {
        Bucket incomplete = null;
        Bucket latest = null;
        for (Bucket b : buckets) {
            if (b == null) {
                continue;
            }
            if (b.pending && (incomplete == null || b.priority < incomplete.priority)) {
                incomplete = b;
            }
            if (latest == null || b.priority > latest.priority) {
                latest = b;
            }
        }
        return incomplete != null ? incomplete : latest;
    }

    private static String bucketKey(Match m) {
        String phase = m.getPhaseCode() == null ? "" : m.getPhaseCode().trim().toUpperCase();
        if ("GROUP".equals(phase)) {
            return "G:" + (m.getGroupId() == null ? 0 : m.getGroupId());
        }
        if ("QUALIFIER".equals(phase)) {
            boolean ko = KO_QUALIFIER_SOURCE.equalsIgnoreCase(m.getCreateSource() == null ? "" : m.getCreateSource());
            int round = m.getQualifierRound() != null ? m.getQualifierRound() : (m.getRound() == null ? 0 : m.getRound());
            return (ko ? "KQ:" : "EQ:") + round;
        }
        int round = m.getRound() == null ? 0 : m.getRound();
        return "KO:" + round;
    }

    private static boolean isWithdrawn(Match m) {
        return m.getStatus() != null && m.getStatus() == 3;
    }

    private static boolean isCompleted(Match m, Map<Long, List<SetScore>> scoresByMatch) {
        List<SetScore> ss = m.getId() == null ? List.of() : scoresByMatch.getOrDefault(m.getId(), List.of());
        if (ss.isEmpty()) {
            return false;
        }
        int p1 = 0;
        int p2 = 0;
        for (SetScore s : ss) {
            if (s == null) {
                continue;
            }
            p1 += s.getPlayer1Score() == null ? 0 : s.getPlayer1Score();
            p2 += s.getPlayer2Score() == null ? 0 : s.getPlayer2Score();
        }
        return !(p1 == 0 && p2 == 0);
    }

    private static Long opponentId(Match m, Long userId) {
        if (Objects.equals(m.getPlayer1Id(), userId)) {
            return m.getPlayer2Id();
        }
        if (Objects.equals(m.getPlayer2Id(), userId)) {
            return m.getPlayer1Id();
        }
        if (Objects.equals(m.getHomeUserId(), userId)) {
            return m.getAwayUserId();
        }
        if (Objects.equals(m.getAwayUserId(), userId)) {
            return m.getHomeUserId();
        }
        return null;
    }

    private static final class Bucket {
        private final int priority;
        private final String label;
        private final String tabId;
        private final Long groupId;
        private final List<Match> matches = new ArrayList<>();
        private boolean pending;

        private Bucket(Match sample, Map<Long, String> groupNameById) {
            String phase = sample.getPhaseCode() == null ? "" : sample.getPhaseCode().trim().toUpperCase();
            if ("GROUP".equals(phase)) {
                this.groupId = sample.getGroupId();
                this.tabId = "match-tab-group-" + (sample.getGroupId() == null ? 0 : sample.getGroupId());
                this.label = groupNameById.getOrDefault(sample.getGroupId(), "小组赛");
                this.priority = 0;
                return;
            }
            this.groupId = null;
            if ("QUALIFIER".equals(phase) && KO_QUALIFIER_SOURCE.equalsIgnoreCase(sample.getCreateSource() == null ? "" : sample.getCreateSource())) {
                int round = sample.getQualifierRound() != null ? sample.getQualifierRound() : (sample.getRound() == null ? 0 : sample.getRound());
                this.tabId = "match-tab-ko-qualifier-" + round;
                this.label = "淘汰赛附加资格赛（通往" + roundLabel(round) + "）";
                this.priority = 1000 - round;
                return;
            }
            if ("QUALIFIER".equals(phase)) {
                int round = sample.getQualifierRound() != null ? sample.getQualifierRound() : (sample.getRound() == null ? 1 : sample.getRound());
                this.tabId = "match-tab-qualifier-" + round;
                this.label = "正赛资格赛";
                this.priority = 500 + round;
                return;
            }
            int round = sample.getRound() == null ? 0 : sample.getRound();
            this.tabId = "match-tab-ko-" + round;
            this.label = roundLabel(round);
            this.priority = 2000 - round;
        }
    }

    private static String roundLabel(int v) {
        return switch (v) {
            case 16 -> "1/16决赛";
            case 8 -> "1/8决赛";
            case 4 -> "1/4决赛";
            case 2 -> "半决赛";
            case 1 -> "奖牌赛（金/铜）";
            default -> "第 " + v + " 轮";
        };
    }
}
