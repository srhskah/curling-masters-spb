package com.example.service;

import com.example.entity.Match;
import com.example.entity.MatchAcceptance;
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
            for (Long uid : uids) {
                Map<String, Object> row = stat.get(uid);
                if (row == null) {
                    continue;
                }
                int expected = (int) gm.stream()
                        .filter(m -> Objects.equals(m.getPlayer1Id(), uid) || Objects.equals(m.getPlayer2Id(), uid))
                        .count();
                row.put("expectedGroupMatches", expected);
            }
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
            int p1Reg = ss.stream().filter(s -> s.getSetNumber() != null && s.getSetNumber() <= regularSets).mapToInt(s -> s.getPlayer1Score() == null ? 0 : s.getPlayer1Score()).sum();
            int p2Reg = ss.stream().filter(s -> s.getSetNumber() != null && s.getSetNumber() <= regularSets).mapToInt(s -> s.getPlayer2Score() == null ? 0 : s.getPlayer2Score()).sum();
            if (p1Total == 0 && p2Total == 0) {
                continue;
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
            if (p1Total == p2Total) {
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

    /**
     * 检测伪小组并按伪小组内名次，把同分块在真实小组内的 groupRank 连续化为 blockStart~blockStart+n-1，最后按 groupRank 重排每组列表顺序。
     * acceptByMatch 可为空 Map（仅影响导出用伪小组场次里的验收时间）。
     */
    public List<Map<String, Object>> buildPseudoGroupExportRowsAndApplyMainRanks(
            List<TournamentGroup> groups,
            Map<Long, List<Map<String, Object>>> rankingByGroupId,
            List<Match> groupMatches,
            Map<Long, List<SetScore>> setByMatch,
            Map<Long, List<MatchAcceptance>> acceptByMatch,
            Map<Long, String> usernameById,
            boolean allowDraw,
            int regularSets) {
        List<Map<String, Object>> pseudoGroupRows = new ArrayList<>();
        Map<Long, List<MatchAcceptance>> acceptSafe = acceptByMatch != null ? acceptByMatch : Map.of();
        for (TournamentGroup g : groups) {
            List<Map<String, Object>> ranking = rankingByGroupId.get(g.getId());
            if (ranking == null) {
                continue;
            }
            List<Match> gm = groupMatches.stream()
                    .filter(m -> "GROUP".equalsIgnoreCase(m.getPhaseCode()) && Objects.equals(m.getGroupId(), g.getId()))
                    .toList();
            Map<Integer, List<Map<String, Object>>> byPoints = ranking.stream().collect(Collectors.groupingBy(
                    r -> (Integer) r.getOrDefault("points", 0), LinkedHashMap::new, Collectors.toList()));
            for (Map.Entry<Integer, List<Map<String, Object>>> e : byPoints.entrySet()) {
                List<Map<String, Object>> tie = e.getValue();
                if (tie.size() < 3) {
                    continue;
                }
                Set<Long> tieIds = tie.stream()
                        .map(r -> (Long) r.get("userId"))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                List<Match> tieMatches = gm.stream()
                        .filter(m -> tieIds.contains(m.getPlayer1Id()) && tieIds.contains(m.getPlayer2Id()))
                        .toList();
                if (tieMatches.isEmpty()) {
                    continue;
                }
                String rawGroupName = g.getGroupName() == null ? "-" : g.getGroupName();
                String baseGroupName = rawGroupName.endsWith("组") ? rawGroupName.substring(0, rawGroupName.length() - 1) : rawGroupName;
                String pseudoGroupName = baseGroupName + "'组";

                Map<Long, List<Long>> pseudoMember = Map.of(g.getId(), new ArrayList<>(tieIds));
                List<TournamentGroup> pseudoGroup = List.of(new TournamentGroup());
                pseudoGroup.get(0).setId(g.getId());
                pseudoGroup.get(0).setGroupName(pseudoGroupName);

                List<Map<String, Object>> pseudoRanking = buildGroupRankingsByMemberIds(
                        pseudoGroup, pseudoMember, usernameById, tieMatches, setByMatch, allowDraw, regularSets
                ).getOrDefault(g.getId(), List.of());

                boolean chainTie = pseudoRanking.size() >= 3;
                if (chainTie) {
                    int w0 = (int) pseudoRanking.get(0).getOrDefault("wins", 0);
                    int d0 = (int) pseudoRanking.get(0).getOrDefault("draws", 0);
                    int l0 = (int) pseudoRanking.get(0).getOrDefault("losses", 0);
                    for (Map<String, Object> row : pseudoRanking) {
                        int w = (int) row.getOrDefault("wins", 0);
                        int d = (int) row.getOrDefault("draws", 0);
                        int l = (int) row.getOrDefault("losses", 0);
                        if (w != w0 || d != d0 || l != l0) {
                            chainTie = false;
                            break;
                        }
                    }
                }

                if (chainTie) {
                    List<Map<String, Object>> reordered = new ArrayList<>(pseudoRanking);
                    reordered.sort((aa, bb) -> {
                        int an = (int) aa.getOrDefault("net", 0), bn = (int) bb.getOrDefault("net", 0);
                        if (an != bn) return Integer.compare(bn, an);
                        int at = (int) aa.getOrDefault("totalScore", 0), bt = (int) bb.getOrDefault("totalScore", 0);
                        if (at != bt) return Integer.compare(bt, at);
                        int a2 = (int) aa.getOrDefault("matchGe2Count", 0), b2 = (int) bb.getOrDefault("matchGe2Count", 0);
                        if (a2 != b2) return Integer.compare(b2, a2);
                        int am = (int) aa.getOrDefault("matchMaxScore", 0), bm = (int) bb.getOrDefault("matchMaxScore", 0);
                        if (am != bm) return Integer.compare(bm, am);
                        int as = (int) aa.getOrDefault("stealCount", 0), bs = (int) bb.getOrDefault("stealCount", 0);
                        if (as != bs) return Integer.compare(bs, as);
                        int ax = (int) aa.getOrDefault("stealMax", 0), bx = (int) bb.getOrDefault("stealMax", 0);
                        if (ax != bx) return Integer.compare(bx, ax);
                        return String.valueOf(aa.getOrDefault("username", "")).compareTo(String.valueOf(bb.getOrDefault("username", "")));
                    });
                    for (int i = 0; i < reordered.size(); i++) {
                        reordered.get(i).put("groupRank", i + 1);
                    }
                    pseudoRanking = reordered;
                }

                remapMainGroupRanksFromPseudoOrder(tie, pseudoRanking);

                Map<String, Object> pg = new LinkedHashMap<>();
                pg.put("groupName", pseudoGroupName);
                pg.put("fromGroup", g.getGroupName());
                pg.put("points", e.getKey());
                pg.put("ranking", pseudoRanking);
                pg.put("matches", tieMatches.stream().map(m -> {
                    List<SetScore> ss = setByMatch.getOrDefault(m.getId(), List.of());
                    int t1 = ss.stream().mapToInt(x -> x.getPlayer1Score() == null ? 0 : x.getPlayer1Score()).sum();
                    int t2 = ss.stream().mapToInt(x -> x.getPlayer2Score() == null ? 0 : x.getPlayer2Score()).sum();
                    List<Map<String, Object>> sets = ss.stream().map(s -> {
                        String p1 = Boolean.TRUE.equals(s.getPlayer1IsX()) ? "X" : String.valueOf(s.getPlayer1Score() == null ? 0 : s.getPlayer1Score());
                        String p2 = Boolean.TRUE.equals(s.getPlayer2IsX()) ? "X" : String.valueOf(s.getPlayer2Score() == null ? 0 : s.getPlayer2Score());
                        String hammer = Objects.equals(s.getHammerPlayerId(), m.getPlayer1Id())
                                ? usernameById.getOrDefault(m.getPlayer1Id(), "待定")
                                : (Objects.equals(s.getHammerPlayerId(), m.getPlayer2Id())
                                ? usernameById.getOrDefault(m.getPlayer2Id(), "待定")
                                : "-");
                        return Map.<String, Object>of(
                                "setNumber", s.getSetNumber(),
                                "player1ScoreText", p1,
                                "player2ScoreText", p2,
                                "hammer", hammer
                        );
                    }).toList();
                    List<MatchAcceptance> ac = acceptSafe.getOrDefault(m.getId(), List.of());
                    String ts = ac.isEmpty() ? "-" : String.valueOf(ac.get(ac.size() - 1).getAcceptedAt());
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("category", m.getCategory() == null ? "-" : m.getCategory());
                    row.put("player1Name", usernameById.getOrDefault(m.getPlayer1Id(), "待定"));
                    row.put("player2Name", usernameById.getOrDefault(m.getPlayer2Id(), "待定"));
                    row.put("score", t1 + ":" + t2);
                    row.put("firstHammerName", sets.isEmpty() ? "-" : String.valueOf(sets.get(0).getOrDefault("hammer", "-")));
                    row.put("sets", sets);
                    row.put("acceptedAt", ts);
                    return row;
                }).toList());
                pseudoGroupRows.add(pg);
            }
        }
        for (TournamentGroup g : groups) {
            List<Map<String, Object>> rows = rankingByGroupId.get(g.getId());
            if (rows != null && !rows.isEmpty()) {
                rows.sort(Comparator.comparingInt(r -> (int) r.getOrDefault("groupRank", 999)));
            }
        }
        return pseudoGroupRows;
    }

    /**
     * 伪小组内名次 pseudoRanking（groupRank 1..n）映射到真实小组同分块在全局中的名次区间。
     */
    private void remapMainGroupRanksFromPseudoOrder(List<Map<String, Object>> mainTieRows, List<Map<String, Object>> pseudoRanking) {
        int blockStart = mainTieRows.stream().mapToInt(r -> (int) r.getOrDefault("groupRank", 999)).min().orElse(1);
        List<Map<String, Object>> order = new ArrayList<>(pseudoRanking);
        order.sort(Comparator.comparingInt(a -> (int) a.getOrDefault("groupRank", 999)));
        for (int i = 0; i < order.size(); i++) {
            Long uid = (Long) order.get(i).get("userId");
            if (uid == null) {
                continue;
            }
            int newRank = blockStart + i;
            for (Map<String, Object> row : mainTieRows) {
                if (Objects.equals(row.get("userId"), uid)) {
                    row.put("groupRank", newRank);
                    break;
                }
            }
        }
    }
}
