package com.example.service;

import com.example.entity.Match;
import com.example.entity.SetScore;
import com.example.entity.TournamentCompetitionConfig;
import com.example.entity.TournamentGroup;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class GroupRankingCalculator {
    public Map<Long, List<Map<String, Object>>> buildGroupRankingsByMemberIds(List<TournamentGroup> groups,
                                                                               Map<Long, List<Long>> memberIdsByGroup,
                                                                               Map<Long, String> usernameById,
                                                                               List<Match> groupMatches,
                                                                               Map<Long, List<SetScore>> scoreByMatch,
                                                                               TournamentCompetitionConfig cfg) {
        boolean allowDraw = cfg == null || !Boolean.FALSE.equals(cfg.getGroupAllowDraw());
        int regularSets = (cfg != null && cfg.getGroupStageSets() != null && cfg.getGroupStageSets() > 0) ? cfg.getGroupStageSets() : 8;
        return buildGroupRankingsByMemberIds(groups, memberIdsByGroup, usernameById, groupMatches, scoreByMatch, allowDraw, regularSets);
    }

    public Map<Long, List<Map<String, Object>>> buildGroupRankingsByMemberIds(List<TournamentGroup> groups,
                                                                               Map<Long, List<Long>> memberIdsByGroup,
                                                                               Map<Long, String> usernameById,
                                                                               List<Match> groupMatches,
                                                                               Map<Long, List<SetScore>> scoreByMatch,
                                                                               boolean allowDraw,
                                                                               int regularSets) {
        Map<Long, List<Map<String, Object>>> out = new HashMap<>();
        for (TournamentGroup g : groups) {
            List<Long> uids = memberIdsByGroup.getOrDefault(g.getId(), List.of()).stream().filter(Objects::nonNull).distinct().toList();
            Map<Long, Map<String, Object>> stat = initGroupStats(uids, usernameById, g.getGroupName());
            List<Match> gm = groupMatches.stream().filter(m -> "GROUP".equalsIgnoreCase(m.getPhaseCode()) && Objects.equals(m.getGroupId(), g.getId())).toList();
            fillGroupStats(stat, gm, scoreByMatch, allowDraw, regularSets);
            out.put(g.getId(), rankRows(new ArrayList<>(stat.values()), gm));
        }
        return out;
    }

    public List<Map<String, Object>> buildOverallRanking(Map<Long, List<Map<String, Object>>> groupRankingByGroupId) {
        List<Map<String, Object>> rows = groupRankingByGroupId.values().stream().flatMap(List::stream).sorted((a, b) -> {
            int ar = (int) a.getOrDefault("groupRank", 999), br = (int) b.getOrDefault("groupRank", 999);
            if (ar != br) return Integer.compare(ar, br);
            int ap = (int) a.getOrDefault("points", 0), bp = (int) b.getOrDefault("points", 0);
            if (ap != bp) return Integer.compare(bp, ap);
            int an = (int) a.getOrDefault("net", 0), bn = (int) b.getOrDefault("net", 0);
            if (an != bn) return Integer.compare(bn, an);
            int at = (int) a.getOrDefault("totalScore", 0), bt = (int) b.getOrDefault("totalScore", 0);
            if (at != bt) return Integer.compare(bt, at);
            int a2 = (int) a.getOrDefault("matchGe2Count", 0), b2 = (int) b.getOrDefault("matchGe2Count", 0);
            if (a2 != b2) return Integer.compare(b2, a2);
            int am = (int) a.getOrDefault("matchMaxScore", 0), bm = (int) b.getOrDefault("matchMaxScore", 0);
            if (am != bm) return Integer.compare(bm, am);
            int as = (int) a.getOrDefault("stealCount", 0), bs = (int) b.getOrDefault("stealCount", 0);
            if (as != bs) return Integer.compare(bs, as);
            int ax = (int) a.getOrDefault("stealMax", 0), bx = (int) b.getOrDefault("stealMax", 0);
            if (ax != bx) return Integer.compare(bx, ax);
            return String.valueOf(a.getOrDefault("username", "")).compareTo(String.valueOf(b.getOrDefault("username", "")));
        }).collect(Collectors.toList());
        for (int i = 0; i < rows.size(); i++) rows.get(i).put("overallRank", i + 1);
        return rows;
    }

    private Map<Long, Map<String, Object>> initGroupStats(List<Long> userIds, Map<Long, String> usernameById, String groupName) {
        Map<Long, Map<String, Object>> stat = new HashMap<>();
        for (Long uid : userIds) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", uid);
            row.put("username", usernameById.getOrDefault(uid, "用户" + uid));
            row.put("groupName", groupName);
            row.put("played", 0); row.put("wins", 0); row.put("draws", 0); row.put("losses", 0);
            row.put("points", 0); row.put("totalScore", 0); row.put("totalConceded", 0); row.put("net", 0);
            // matchGe2Count：每场中「该局得分≥2」的局数，跨场累加（≥2 分局数总和）
            // matchMaxScore：小组赛中单局最高得分（全局取 max，非相加）
            // stealMax：单次偷分最高分（全局取 max）
            row.put("matchGe2Count", 0); row.put("matchMaxScore", 0); row.put("stealCount", 0); row.put("stealMax", 0);
            stat.put(uid, row);
        }
        return stat;
    }

    private void fillGroupStats(Map<Long, Map<String, Object>> stat, List<Match> matches, Map<Long, List<SetScore>> scoreByMatch, boolean allowDraw, int regularSets) {
        for (Match m : matches) {
            Long p1 = m.getPlayer1Id(), p2 = m.getPlayer2Id();
            if (p1 == null || p2 == null || !stat.containsKey(p1) || !stat.containsKey(p2)) continue;
            List<SetScore> ss = scoreByMatch.getOrDefault(m.getId(), List.of());
            if (ss.isEmpty()) continue;
            int p1Total = 0, p2Total = 0, p1Ge2 = 0, p2Ge2 = 0, p1Max = 0, p2Max = 0, p1StealCnt = 0, p2StealCnt = 0, p1StealMax = 0, p2StealMax = 0;
            for (SetScore s : ss) {
                int a = s.getPlayer1Score() == null ? 0 : s.getPlayer1Score();
                int b = s.getPlayer2Score() == null ? 0 : s.getPlayer2Score();
                p1Total += a; p2Total += b;
                if (a >= 2) p1Ge2++; if (b >= 2) p2Ge2++;
                p1Max = Math.max(p1Max, a); p2Max = Math.max(p2Max, b);
                if (a > 0 && Objects.equals(s.getHammerPlayerId(), m.getPlayer2Id())) { p1StealCnt++; p1StealMax = Math.max(p1StealMax, a); }
                if (b > 0 && Objects.equals(s.getHammerPlayerId(), m.getPlayer1Id())) { p2StealCnt++; p2StealMax = Math.max(p2StealMax, b); }
            }
            Map<String, Object> r1 = stat.get(p1), r2 = stat.get(p2);
            addInt(r1, "played", 1); addInt(r2, "played", 1);
            addInt(r1, "totalScore", p1Total); addInt(r1, "totalConceded", p2Total);
            addInt(r2, "totalScore", p2Total); addInt(r2, "totalConceded", p1Total);
            addInt(r1, "net", p1Total - p2Total); addInt(r2, "net", p2Total - p1Total);
            addInt(r1, "matchGe2Count", p1Ge2);
            addInt(r2, "matchGe2Count", p2Ge2);
            maxInt(r1, "matchMaxScore", p1Max);
            maxInt(r2, "matchMaxScore", p2Max);
            addInt(r1, "stealCount", p1StealCnt);
            addInt(r2, "stealCount", p2StealCnt);
            maxInt(r1, "stealMax", p1StealMax);
            maxInt(r2, "stealMax", p2StealMax);
            int p1Reg = ss.stream().filter(s -> s.getSetNumber() != null && s.getSetNumber() <= regularSets).mapToInt(s -> s.getPlayer1Score() == null ? 0 : s.getPlayer1Score()).sum();
            int p2Reg = ss.stream().filter(s -> s.getSetNumber() != null && s.getSetNumber() <= regularSets).mapToInt(s -> s.getPlayer2Score() == null ? 0 : s.getPlayer2Score()).sum();
            if (p1Total == 0 && p2Total == 0) {
                continue;
            } else if (p1Total == p2Total) {
                addInt(r1, "draws", 1); addInt(r2, "draws", 1); addInt(r1, "points", 1); addInt(r2, "points", 1);
            } else if (p1Total > p2Total) {
                addInt(r1, "wins", 1); addInt(r2, "losses", 1);
                addInt(r1, "points", (!allowDraw && p1Reg == p2Reg) ? 2 : 3);
                addInt(r2, "points", (!allowDraw && p1Reg == p2Reg) ? 1 : 0);
            } else {
                addInt(r2, "wins", 1); addInt(r1, "losses", 1);
                addInt(r2, "points", (!allowDraw && p1Reg == p2Reg) ? 2 : 3);
                addInt(r1, "points", (!allowDraw && p1Reg == p2Reg) ? 1 : 0);
            }
        }
    }

    private List<Map<String, Object>> rankRows(List<Map<String, Object>> rows, List<Match> groupMatches) {
        rows.sort((a, b) -> {
            int ap = (int) a.getOrDefault("points", 0), bp = (int) b.getOrDefault("points", 0);
            if (ap != bp) return Integer.compare(bp, ap);
            int hh = compareHeadToHeadTwo(a, b, groupMatches);
            if (hh != 0) return hh;
            int an = (int) a.getOrDefault("net", 0), bn = (int) b.getOrDefault("net", 0);
            if (an != bn) return Integer.compare(bn, an);
            int at = (int) a.getOrDefault("totalScore", 0), bt = (int) b.getOrDefault("totalScore", 0);
            if (at != bt) return Integer.compare(bt, at);
            int a2 = (int) a.getOrDefault("matchGe2Count", 0), b2 = (int) b.getOrDefault("matchGe2Count", 0);
            if (a2 != b2) return Integer.compare(b2, a2);
            int am = (int) a.getOrDefault("matchMaxScore", 0), bm = (int) b.getOrDefault("matchMaxScore", 0);
            if (am != bm) return Integer.compare(bm, am);
            int as = (int) a.getOrDefault("stealCount", 0), bs = (int) b.getOrDefault("stealCount", 0);
            if (as != bs) return Integer.compare(bs, as);
            int ax = (int) a.getOrDefault("stealMax", 0), bx = (int) b.getOrDefault("stealMax", 0);
            if (ax != bx) return Integer.compare(bx, ax);
            return String.valueOf(a.getOrDefault("username", "")).compareTo(String.valueOf(b.getOrDefault("username", "")));
        });
        for (int i = 0; i < rows.size(); i++) rows.get(i).put("groupRank", i + 1);
        return rows;
    }

    private int compareHeadToHeadTwo(Map<String, Object> a, Map<String, Object> b, List<Match> groupMatches) {
        Long ua = (Long) a.get("userId"), ub = (Long) b.get("userId");
        if (ua == null || ub == null) return 0;
        for (Match m : groupMatches) {
            Long p1 = m.getPlayer1Id(), p2 = m.getPlayer2Id();
            boolean ab = Objects.equals(p1, ua) && Objects.equals(p2, ub);
            boolean ba = Objects.equals(p1, ub) && Objects.equals(p2, ua);
            if (!ab && !ba) continue;
            if (m.getWinnerId() == null) return 0;
            if (Objects.equals(m.getWinnerId(), ua)) return -1;
            if (Objects.equals(m.getWinnerId(), ub)) return 1;
            return 0;
        }
        return 0;
    }

    private void addInt(Map<String, Object> row, String key, int delta) {
        row.put(key, ((Number) row.getOrDefault(key, 0)).intValue() + delta);
    }

    private void maxInt(Map<String, Object> row, String key, int candidate) {
        int cur = ((Number) row.getOrDefault(key, 0)).intValue();
        row.put(key, Math.max(cur, candidate));
    }
}
