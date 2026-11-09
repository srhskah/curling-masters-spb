package com.example.service;

import com.example.entity.Match;
import com.example.entity.SetScore;
import com.example.entity.TournamentGroup;
import com.example.ranking.RankingCriterion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupRankingCalculatorPseudoGroupTest {

    private final GroupRankingCalculator calculator = new GroupRankingCalculator();

    @Test
    void samePointsSplitByWdlThenInnerHeadToHead() {
        // A,B 均为 2胜1负且都赢了 C,D；C,D 均为 1胜2负。伪小组应先 A/B 后 C/D，
        // 再在 {A,B}、{C,D} 内按相互交锋排名。
        TournamentGroup group = group(1L, "A组");
        Map<Long, String> names = Map.of(10L, "A", 20L, "B", 30L, "C", 40L, "D");
        Map<Long, List<SetScore>> scores = new HashMap<>();
        List<Match> matches = new ArrayList<>();
        long mid = 1L;
        mid = add(matches, scores, mid, group.getId(), 10L, 20L, 5, 1);
        mid = add(matches, scores, mid, group.getId(), 10L, 30L, 4, 1);
        mid = add(matches, scores, mid, group.getId(), 40L, 10L, 3, 1);
        mid = add(matches, scores, mid, group.getId(), 20L, 30L, 4, 1);
        mid = add(matches, scores, mid, group.getId(), 20L, 40L, 4, 1);
        add(matches, scores, mid, group.getId(), 30L, 40L, 4, 1);

        List<Map<String, Object>> ranking = List.of(
                row(10L, "A", 4, 2),
                row(20L, "B", 4, 1),
                row(30L, "C", 4, 3),
                row(40L, "D", 4, 4)
        );
        Map<Long, List<Map<String, Object>>> byGroup = new HashMap<>();
        byGroup.put(group.getId(), new ArrayList<>(ranking));

        List<Map<String, Object>> pseudo = calculator.buildPseudoGroupExportRowsAndApplyMainRanks(
                List.of(group), byGroup, matches, scores, Map.of(), names, true, 8);

        assertEquals(1, pseudo.size(), "导出只保留最外层伪小组");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pseudoRanking = (List<Map<String, Object>>) pseudo.get(0).get("ranking");
        assertEquals(List.of("A", "B", "C", "D"), namesInOrder(pseudoRanking));
        assertEquals(List.of("A", "B", "C", "D"), namesInOrder(byGroup.get(group.getId())));
        assertEquals(1, byGroup.get(group.getId()).get(0).get("groupRank"));
        assertEquals(4, byGroup.get(group.getId()).get(3).get("groupRank"));
    }

    @Test
    void recursiveInnerSplitAmongWinnersThenAmongLosers() {
        // A,B,C 都赢了 D，三人同为 2胜1负；A>B、B>C、C>A 循环，内层再用净胜分拆开。
        TournamentGroup group = group(2L, "B组");
        Map<Long, String> names = Map.of(1L, "A", 2L, "B", 3L, "C", 4L, "D");
        Map<Long, List<SetScore>> scores = new HashMap<>();
        List<Match> matches = new ArrayList<>();
        long mid = 100L;
        mid = add(matches, scores, mid, group.getId(), 1L, 2L, 8, 1);
        mid = add(matches, scores, mid, group.getId(), 2L, 3L, 6, 1);
        mid = add(matches, scores, mid, group.getId(), 3L, 1L, 3, 2);
        mid = add(matches, scores, mid, group.getId(), 1L, 4L, 2, 0);
        mid = add(matches, scores, mid, group.getId(), 2L, 4L, 2, 0);
        add(matches, scores, mid, group.getId(), 3L, 4L, 2, 0);

        List<Map<String, Object>> ranking = List.of(
                row(1L, "A", 6, 4),
                row(2L, "B", 6, 3),
                row(3L, "C", 6, 2),
                row(4L, "D", 6, 1)
        );
        Map<Long, List<Map<String, Object>>> byGroup = new HashMap<>();
        byGroup.put(group.getId(), new ArrayList<>(ranking));

        List<Map<String, Object>> pseudo = calculator.buildPseudoGroupExportRowsAndApplyMainRanks(
                List.of(group), byGroup, matches, scores, Map.of(), names, true, 8);

        assertEquals(1, pseudo.size());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pseudoRanking = (List<Map<String, Object>>) pseudo.get(0).get("ranking");
        List<String> order = namesInOrder(pseudoRanking);
        assertEquals("D", order.get(3), "全败者应在伪小组末位");
        assertTrue(order.subList(0, 3).containsAll(List.of("A", "B", "C")));
        assertEquals(order, namesInOrder(byGroup.get(group.getId())), "小组名次应跟随伪小组内排名");
    }

    @Test
    void uniqueSameWdlTieDetectedAndGroupModeSkipsPseudo() {
        TournamentGroup group = group(3L, "C组");
        group.setRankTieMode("GROUP");
        Map<Long, String> names = Map.of(1L, "A", 2L, "B", 3L, "C", 4L, "D");
        Map<Long, List<SetScore>> scores = new HashMap<>();
        List<Match> matches = new ArrayList<>();
        long mid = 200L;
        mid = add(matches, scores, mid, group.getId(), 1L, 2L, 8, 1);
        mid = add(matches, scores, mid, group.getId(), 2L, 3L, 6, 1);
        mid = add(matches, scores, mid, group.getId(), 3L, 1L, 3, 2);
        mid = add(matches, scores, mid, group.getId(), 1L, 4L, 2, 0);
        mid = add(matches, scores, mid, group.getId(), 2L, 4L, 2, 0);
        add(matches, scores, mid, group.getId(), 3L, 4L, 2, 0);

        List<Map<String, Object>> ranking = List.of(
                wdlRow(1L, "A", 6, 1, 2, 0, 1),
                wdlRow(2L, "B", 6, 2, 2, 0, 1),
                wdlRow(3L, "C", 6, 3, 2, 0, 1),
                wdlRow(4L, "D", 0, 4, 0, 0, 3)
        );
        Map<Long, List<Map<String, Object>>> byGroup = new HashMap<>();
        byGroup.put(group.getId(), new ArrayList<>(ranking));

        assertTrue(calculator.isUniqueSameWdlPointTie(ranking));
        List<Map<String, Object>> pseudo = calculator.buildPseudoGroupExportRowsAndApplyMainRanks(
                List.of(group), byGroup, matches, scores, Map.of(), names, true, 8);
        assertTrue(pseudo.isEmpty(), "选用小组数据时不导出伪小组");
        assertEquals(List.of("A", "B", "C", "D"), namesInOrder(byGroup.get(group.getId())));
        assertEquals(1, byGroup.get(group.getId()).get(0).get("groupRank"));
    }

    @Test
    void groupStatsModeOrdersUniqueTieByNetNotHeadToHead() {
        TournamentGroup group = group(4L, "D组");
        group.setRankTieMode("GROUP");
        Map<Long, String> names = Map.of(1L, "胖虎", 2L, "卍", 3L, "Lonely", 4L, "头名");
        List<Map<String, Object>> ranking = List.of(
                wdlRow(4L, "头名", 12, 1, 4, 0, 1, 9),
                wdlRow(1L, "胖虎", 9, 2, 3, 0, 2, 6),
                wdlRow(2L, "卍", 9, 3, 3, 0, 2, -2),
                wdlRow(3L, "Lonely", 9, 4, 3, 0, 2, 4)
        );
        Map<Long, List<Map<String, Object>>> byGroup = new HashMap<>();
        byGroup.put(group.getId(), new ArrayList<>(ranking));
        calculator.buildPseudoGroupExportRowsAndApplyMainRanks(
                List.of(group), byGroup, List.of(), Map.of(), Map.of(), names, true, 8);
        assertEquals(List.of("头名", "胖虎", "Lonely", "卍"), namesInOrder(byGroup.get(group.getId())));
    }

    @Test
    void pseudoGroupUsesOnlyCriteriaStillEnabledForTournament() {
        TournamentGroup group = group(5L, "E组");
        Map<Long, String> names = Map.of(10L, "Zed", 20L, "Ann", 30L, "Dee", 40L, "Cat");
        Map<Long, List<SetScore>> scores = new HashMap<>();
        List<Match> matches = new ArrayList<>();
        long mid = 300L;
        mid = add(matches, scores, mid, group.getId(), 10L, 20L, 5, 1);
        mid = add(matches, scores, mid, group.getId(), 10L, 30L, 4, 1);
        mid = add(matches, scores, mid, group.getId(), 40L, 10L, 3, 1);
        mid = add(matches, scores, mid, group.getId(), 20L, 30L, 4, 1);
        mid = add(matches, scores, mid, group.getId(), 20L, 40L, 4, 1);
        add(matches, scores, mid, group.getId(), 30L, 40L, 4, 1);

        List<Map<String, Object>> ranking = List.of(
                row(10L, "Zed", 4, 1),
                row(20L, "Ann", 4, 2),
                row(30L, "Dee", 4, 3),
                row(40L, "Cat", 4, 4)
        );
        Map<Long, List<Map<String, Object>>> byGroup = new HashMap<>();
        byGroup.put(group.getId(), new ArrayList<>(ranking));

        List<RankingCriterion> pointsThenName = List.of(RankingCriterion.POINTS, RankingCriterion.USERNAME);
        List<Map<String, Object>> pseudo = calculator.buildPseudoGroupExportRowsAndApplyMainRanks(
                List.of(group), byGroup, matches, scores, Map.of(), names, true, 8, pointsThenName);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pseudoRanking = (List<Map<String, Object>>) pseudo.get(0).get("ranking");
        assertEquals(List.of("Ann", "Zed", "Cat", "Dee"), namesInOrder(pseudoRanking));
        assertEquals(List.of("Ann", "Zed", "Cat", "Dee"), namesInOrder(byGroup.get(group.getId())));
    }

    @Test
    void groupStatsModeDoesNotReintroduceRemovedCriteria() {
        TournamentGroup group = group(6L, "F组");
        group.setRankTieMode("GROUP");
        Map<Long, String> names = Map.of(1L, "胖虎", 2L, "卍", 3L, "Lonely", 4L, "头名");
        List<Map<String, Object>> ranking = List.of(
                wdlRow(4L, "头名", 12, 1, 4, 0, 1, 9),
                wdlRow(3L, "Lonely", 9, 2, 3, 0, 2, 4),
                wdlRow(2L, "卍", 9, 3, 3, 0, 2, -2),
                wdlRow(1L, "胖虎", 9, 4, 3, 0, 2, 6)
        );
        Map<Long, List<Map<String, Object>>> byGroup = new HashMap<>();
        byGroup.put(group.getId(), new ArrayList<>(ranking));
        calculator.buildPseudoGroupExportRowsAndApplyMainRanks(
                List.of(group), byGroup, List.of(), Map.of(), Map.of(), names, true, 8,
                List.of(RankingCriterion.HEAD_TO_HEAD));
        assertEquals(List.of("头名", "胖虎", "卍", "Lonely"), namesInOrder(byGroup.get(group.getId())));
    }

    @Test
    void circularHeadToHeadFallsThroughToTheNextSelectedCriterion() {
        TournamentGroup group = group(7L, "A组");
        Map<Long, String> names = Map.of(1L, "Anchor", 2L, "禾谷", 3L, "Petrichor");
        Map<Long, List<SetScore>> scores = new HashMap<>();
        List<Match> matches = new ArrayList<>();
        long mid = 1800L;
        mid = add(matches, scores, mid, group.getId(), 1L, 2L, 6, 5);
        mid = add(matches, scores, mid, group.getId(), 2L, 3L, 6, 5);
        add(matches, scores, mid, group.getId(), 3L, 1L, 5, 3);

        List<RankingCriterion> withNet = List.of(
                RankingCriterion.POINTS, RankingCriterion.HEAD_TO_HEAD, RankingCriterion.NET, RankingCriterion.USERNAME);
        Map<Long, List<Map<String, Object>>> byGroup = new HashMap<>();
        byGroup.put(group.getId(), new ArrayList<>(List.of(
                row(1L, "Anchor", 3, 1),
                row(2L, "禾谷", 3, 2),
                row(3L, "Petrichor", 3, 3))));
        List<Map<String, Object>> pseudo = calculator.buildPseudoGroupExportRowsAndApplyMainRanks(
                List.of(group), byGroup, matches, scores, Map.of(), names, false, 8, withNet);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pseudoRanking = (List<Map<String, Object>>) pseudo.get(0).get("ranking");
        assertEquals(List.of("Petrichor", "禾谷", "Anchor"), namesInOrder(pseudoRanking));
        assertEquals(List.of("Petrichor", "禾谷", "Anchor"), namesInOrder(byGroup.get(group.getId())));

        Map<Long, List<Map<String, Object>>> byName = new HashMap<>();
        byName.put(group.getId(), new ArrayList<>(List.of(
                row(1L, "Anchor", 3, 1),
                row(2L, "禾谷", 3, 2),
                row(3L, "Petrichor", 3, 3))));
        calculator.buildPseudoGroupExportRowsAndApplyMainRanks(
                List.of(group), byName, matches, scores, Map.of(), names, false, 8,
                List.of(RankingCriterion.POINTS, RankingCriterion.HEAD_TO_HEAD, RankingCriterion.USERNAME));
        assertEquals(List.of("Anchor", "Petrichor", "禾谷"), namesInOrder(byName.get(group.getId())));
    }

    @Test
    void groupModeKeepsTableOrderWhenHeadToHeadIsCircular() {
        TournamentGroup group = group(8L, "A组");
        group.setRankTieMode("GROUP");
        Map<Long, String> names = Map.of(1L, "Anchor", 2L, "禾谷", 3L, "Petrichor");
        Map<Long, List<SetScore>> scores = new HashMap<>();
        List<Match> matches = new ArrayList<>();
        long mid = 1900L;
        mid = add(matches, scores, mid, group.getId(), 1L, 2L, 6, 5);
        mid = add(matches, scores, mid, group.getId(), 2L, 3L, 6, 5);
        add(matches, scores, mid, group.getId(), 3L, 1L, 5, 3);
        Map<Long, List<Map<String, Object>>> byGroup = new HashMap<>();
        byGroup.put(group.getId(), new ArrayList<>(List.of(
                wdlRow(1L, "Anchor", 9, 1, 3, 0, 2, 8),
                wdlRow(2L, "禾谷", 9, 2, 3, 0, 2, 5),
                wdlRow(3L, "Petrichor", 9, 3, 3, 0, 2, 4))));
        List<Map<String, Object>> pseudo = calculator.buildPseudoGroupExportRowsAndApplyMainRanks(
                List.of(group), byGroup, matches, scores, Map.of(), names, false, 8, null);
        assertTrue(pseudo.isEmpty());
        assertEquals(List.of("Anchor", "禾谷", "Petrichor"), namesInOrder(byGroup.get(group.getId())));
    }

    @Test
    void exactTwoPlayersOnEachPointTotalRankByWhoBeatWhom() {
        TournamentGroup group = group(9L, "G组");
        Map<Long, String> names = Map.of(
                1L, "A", 2L, "B", 3L, "C", 4L, "D",
                5L, "E", 6L, "F", 7L, "G", 8L, "H");
        Map<Long, List<SetScore>> scores = new HashMap<>();
        List<Match> matches = new ArrayList<>();
        long mid = 900L;
        mid = add(matches, scores, mid, group.getId(), 1L, 2L, 6, 5);
        mid = add(matches, scores, mid, group.getId(), 2L, 3L, 10, 1);
        mid = add(matches, scores, mid, group.getId(), 3L, 1L, 10, 1);
        mid = add(matches, scores, mid, group.getId(), 1L, 4L, 2, 0);
        mid = add(matches, scores, mid, group.getId(), 2L, 4L, 2, 0);
        mid = add(matches, scores, mid, group.getId(), 5L, 6L, 6, 5);
        mid = add(matches, scores, mid, group.getId(), 6L, 7L, 10, 1);
        mid = add(matches, scores, mid, group.getId(), 7L, 5L, 10, 1);
        mid = add(matches, scores, mid, group.getId(), 5L, 8L, 4, 4);
        add(matches, scores, mid, group.getId(), 6L, 8L, 4, 4);

        List<Long> members = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
        Map<Long, List<Map<String, Object>>> ranked = calculator.buildGroupRankingsByMemberIds(
                List.of(group), Map.of(group.getId(), members), names, matches, scores, false, 8, null);
        assertEquals(List.of("A", "B", "E", "F", "C", "G", "H", "D"), namesInOrder(ranked.get(group.getId())));

        Map<Long, List<Map<String, Object>>> withoutHeadToHead = calculator.buildGroupRankingsByMemberIds(
                List.of(group), Map.of(group.getId(), members), names, matches, scores, false, 8,
                List.of(RankingCriterion.POINTS, RankingCriterion.NET, RankingCriterion.USERNAME));
        assertEquals(List.of("B", "A", "F", "E", "C", "G", "H", "D"), namesInOrder(withoutHeadToHead.get(group.getId())));
    }

    private static TournamentGroup group(Long id, String name) {
        TournamentGroup g = new TournamentGroup();
        g.setId(id);
        g.setGroupName(name);
        return g;
    }

    private static Map<String, Object> row(Long userId, String username, int points, int groupRank) {
        return wdlRow(userId, username, points, groupRank, 0, 0, 0);
    }

    private static Map<String, Object> wdlRow(Long userId, String username, int points, int groupRank,
                                              int wins, int draws, int losses) {
        return wdlRow(userId, username, points, groupRank, wins, draws, losses, 0);
    }

    private static Map<String, Object> wdlRow(Long userId, String username, int points, int groupRank,
                                              int wins, int draws, int losses, int net) {
        Map<String, Object> r = new HashMap<>();
        r.put("userId", userId);
        r.put("username", username);
        r.put("points", points);
        r.put("groupRank", groupRank);
        r.put("wins", wins);
        r.put("draws", draws);
        r.put("losses", losses);
        r.put("net", net);
        r.put("totalScore", 0);
        return r;
    }

    private static long add(List<Match> matches, Map<Long, List<SetScore>> scores, long matchId,
                            Long groupId, Long p1, Long p2, int s1, int s2) {
        Match m = new Match();
        m.setId(matchId);
        m.setPhaseCode("GROUP");
        m.setGroupId(groupId);
        m.setPlayer1Id(p1);
        m.setPlayer2Id(p2);
        m.setCategory("小组赛");
        if (s1 != s2) {
            m.setWinnerId(s1 > s2 ? p1 : p2);
        }
        matches.add(m);
        SetScore ss = new SetScore();
        ss.setMatchId(matchId);
        ss.setSetNumber(1);
        ss.setPlayer1Score(s1);
        ss.setPlayer2Score(s2);
        scores.put(matchId, List.of(ss));
        return matchId + 1;
    }

    private static List<String> namesInOrder(List<Map<String, Object>> rows) {
        List<String> names = new ArrayList<>();
        if (rows == null) {
            return names;
        }
        for (Map<String, Object> row : rows) {
            names.add(String.valueOf(row.get("username")));
        }
        return names;
    }
}
