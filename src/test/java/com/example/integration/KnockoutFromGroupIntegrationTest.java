package com.example.integration;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.demo.DemoApplication;
import com.example.entity.*;
import com.example.mapper.TournamentGroupMapper;
import com.example.service.*;
import com.example.service.impl.KnockoutBracketService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = DemoApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("draw-it")
@Transactional
@Tag("integration")
class KnockoutFromGroupIntegrationTest {

    private static final AtomicLong NAME_SEQ = new AtomicLong();
    private static volatile MySQLContainer<?> testcontainersMysql;

    @DynamicPropertySource
    static void registerDs(DynamicPropertyRegistry registry) {
        String envUrl = System.getenv("DRAW_IT_JDBC_URL");
        if (envUrl != null && !envUrl.isBlank()) {
            String u = firstNonBlank(System.getenv("DRAW_IT_DB_USER"), "root");
            String p = firstNonBlank(System.getenv("DRAW_IT_DB_PASSWORD"), "");
            registry.add("spring.datasource.url", () -> envUrl.trim());
            registry.add("spring.datasource.username", () -> u);
            registry.add("spring.datasource.password", () -> p);
            registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
            return;
        }
        MySQLContainer<?> c = testcontainersMysql;
        if (c == null) {
            synchronized (KnockoutFromGroupIntegrationTest.class) {
                c = testcontainersMysql;
                if (c == null) {
                    c = new MySQLContainer<>("mysql:8.0.36")
                            .withDatabaseName("draw_it")
                            .withUsername("test")
                            .withPassword("test");
                    try {
                        c.start();
                    } catch (RuntimeException ex) {
                        throw new IllegalStateException(
                                "未配置 DRAW_IT_JDBC_URL，且 Testcontainers 无法启动 MySQL。原因: " + ex.getMessage(), ex);
                    }
                    testcontainersMysql = c;
                }
            }
        }
        MySQLContainer<?> container = c;
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    private static String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : (b != null ? b : "");
    }

    @MockBean
    private INotificationService notificationService;

    @Autowired private UserService userService;
    @Autowired private SeasonService seasonService;
    @Autowired private SeriesService seriesService;
    @Autowired private ITournamentLevelService tournamentLevelService;
    @Autowired private TournamentService tournamentService;
    @Autowired private ITournamentRegistrationService registrationService;
    @Autowired private ITournamentCompetitionService competitionService;
    @Autowired private IMatchService matchService;
    @Autowired private UserTournamentPointsService userTournamentPointsService;
    @Autowired private TournamentGroupMapper tournamentGroupMapper;
    @Autowired private KnockoutBracketService knockoutBracketService;

    private static final List<String> WIN_SCORES = List.of("1", "1", "1", "1", "1", "1", "1", "1");
    private static final List<String> LOSE_SCORES = List.of("0", "0", "0", "0", "0", "0", "0", "0");

    private String uniq(String prefix) {
        return prefix + NAME_SEQ.incrementAndGet();
    }

    private User createHost() {
        return userService.register(uniq("koh_"), "pw123456", uniq("koh") + "@t.com");
    }

    private List<User> createPlayers(int n, String pfx) {
        List<User> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(userService.register(uniq(pfx + "_"), "pw123456", uniq("kp") + i + "@t.com"));
        }
        return list;
    }

    @Test
    @DisplayName("8人2组：小组赛全验收后生成1/8淘汰赛首轮4场")
    void generateKnockoutAfterGroupStage() {
        User host = createHost();
        List<User> players = createPlayers(8, "ko");
        long tid = setupGroupTournament(host, 8, 4);
        registerAll(tid, players);
        closeRegistration(host, tid);

        List<TournamentGroup> groups = tournamentGroupMapper.selectList(
                Wrappers.<TournamentGroup>lambdaQuery()
                        .eq(TournamentGroup::getTournamentId, tid)
                        .orderByAsc(TournamentGroup::getGroupOrder));
        assertEquals(2, groups.size());
        Map<Long, List<Long>> roster = new LinkedHashMap<>();
        roster.put(groups.get(0).getId(), players.subList(0, 4).stream().map(User::getId).toList());
        roster.put(groups.get(1).getId(), players.subList(4, 8).stream().map(User::getId).toList());
        competitionService.saveGroupMembers(host, tid, roster);
        competitionService.generateGroupMatches(host, tid);

        List<Match> groupMatches = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tid)
                .eq(Match::getPhaseCode, "GROUP")
                .list();
        assertEquals(12, groupMatches.size());

        for (Match m : groupMatches) {
            boolean p1Wins = m.getPlayer1Id() != null && m.getPlayer1Id() < m.getPlayer2Id();
            List<String> s1 = p1Wins ? WIN_SCORES : LOSE_SCORES;
            List<String> s2 = p1Wins ? LOSE_SCORES : WIN_SCORES;
            competitionService.saveMatchScore(host, m.getId(), 1, s1, s2, false, null, false);
            User u1 = userService.getById(m.getPlayer1Id());
            User u2 = userService.getById(m.getPlayer2Id());
            competitionService.acceptMatchScore(u1, m.getId(), "it");
            competitionService.acceptMatchScore(u2, m.getId(), "it");
        }

        assertTrue(knockoutBracketService.isGroupStageFullyLocked(tid));
        int n = knockoutBracketService.generateFirstKnockoutRound(host, tid);
        assertEquals(4, n);

        long ko = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tid)
                .in(Match::getPhaseCode, "MAIN", "FINAL")
                .eq(Match::getRound, 8)
                .count();
        assertEquals(4, ko);
    }

    @Test
    @DisplayName("8人2组：淘汰赛 1/8→1/4→奖牌赛 自动晋级链")
    void knockoutProgressesToGoldAndBronze() {
        User host = createHost();
        List<User> players = createPlayers(8, "k2");
        long tid = setupGroupTournament(host, 8, 4);
        registerAll(tid, players);
        closeRegistration(host, tid);

        List<TournamentGroup> groups = tournamentGroupMapper.selectList(
                Wrappers.<TournamentGroup>lambdaQuery()
                        .eq(TournamentGroup::getTournamentId, tid)
                        .orderByAsc(TournamentGroup::getGroupOrder));
        Map<Long, List<Long>> roster = new LinkedHashMap<>();
        roster.put(groups.get(0).getId(), players.subList(0, 4).stream().map(User::getId).toList());
        roster.put(groups.get(1).getId(), players.subList(4, 8).stream().map(User::getId).toList());
        competitionService.saveGroupMembers(host, tid, roster);
        competitionService.generateGroupMatches(host, tid);

        for (Match m : matchService.lambdaQuery().eq(Match::getTournamentId, tid).eq(Match::getPhaseCode, "GROUP").list()) {
            boolean p1Wins = m.getPlayer1Id() != null && m.getPlayer1Id() < m.getPlayer2Id();
            List<String> s1 = p1Wins ? WIN_SCORES : LOSE_SCORES;
            List<String> s2 = p1Wins ? LOSE_SCORES : WIN_SCORES;
            competitionService.saveMatchScore(host, m.getId(), 1, s1, s2, false, null, false);
            competitionService.acceptMatchScore(userService.getById(m.getPlayer1Id()), m.getId(), "it");
            competitionService.acceptMatchScore(userService.getById(m.getPlayer2Id()), m.getId(), "it");
        }

        knockoutBracketService.generateFirstKnockoutRound(host, tid);

        List<Match> r8 = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tid)
                .eq(Match::getPhaseCode, "MAIN")
                .eq(Match::getRound, 8)
                .orderByAsc(Match::getKnockoutBracketSlot)
                .list();
        assertEquals(4, r8.size());
        for (Match m : r8) {
            acceptKoWithWinner(host, m, true);
        }

        long r4 = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tid)
                .eq(Match::getRound, 4)
                .in(Match::getPhaseCode, "MAIN", "FINAL")
                .count();
        assertEquals(2, r4);

        List<Match> semis = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tid)
                .eq(Match::getRound, 4)
                .eq(Match::getPhaseCode, "MAIN")
                .orderByAsc(Match::getKnockoutBracketSlot)
                .list();
        for (Match m : semis) {
            acceptKoWithWinner(host, m, true);
        }

        long r1 = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tid)
                .eq(Match::getRound, 1)
                .in(Match::getPhaseCode, "MAIN", "FINAL")
                .count();
        assertEquals(2, r1);
        assertEquals(1L, matchService.lambdaQuery()
                .eq(Match::getTournamentId, tid)
                .eq(Match::getRound, 1)
                .eq(Match::getPhaseCode, "FINAL")
                .count());
        assertEquals(1L, matchService.lambdaQuery()
                .eq(Match::getTournamentId, tid)
                .eq(Match::getRound, 1)
                .eq(Match::getPhaseCode, "MAIN")
                .like(Match::getCategory, "铜")
                .count());
    }

    @Test
    @DisplayName("8人2组：首轮为1/4时，1/4验收后必须先生成半决赛，不能直接生成金铜牌赛")
    void quarterFinalDoesNotSkipToMedalMatches() {
        User host = createHost();
        List<User> players = createPlayers(8, "kq");
        long tid = setupGroupTournament(host, 8, 4);
        registerAll(tid, players);
        closeRegistration(host, tid);

        TournamentCompetitionConfig cfgPatch = new TournamentCompetitionConfig();
        cfgPatch.setTournamentId(tid);
        cfgPatch.setKnockoutStartRound(4);
        competitionService.saveConfig(host, cfgPatch);

        List<TournamentGroup> groups = tournamentGroupMapper.selectList(
                Wrappers.<TournamentGroup>lambdaQuery()
                        .eq(TournamentGroup::getTournamentId, tid)
                        .orderByAsc(TournamentGroup::getGroupOrder));
        Map<Long, List<Long>> roster = new LinkedHashMap<>();
        roster.put(groups.get(0).getId(), players.subList(0, 4).stream().map(User::getId).toList());
        roster.put(groups.get(1).getId(), players.subList(4, 8).stream().map(User::getId).toList());
        competitionService.saveGroupMembers(host, tid, roster);
        competitionService.generateGroupMatches(host, tid);
        lockAllGroupMatches(host, tid);

        int generated = knockoutBracketService.generateFirstKnockoutRound(host, tid);
        assertEquals(4, generated);

        List<Match> quarterFinals = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tid)
                .eq(Match::getPhaseCode, "MAIN")
                .eq(Match::getRound, 4)
                .orderByAsc(Match::getKnockoutBracketSlot)
                .list();
        assertEquals(4, quarterFinals.size());
        for (Match m : quarterFinals) {
            acceptKoWithWinner(host, m, true);
        }

        long medalMatchesPremature = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tid)
                .eq(Match::getRound, 1)
                .in(Match::getPhaseCode, "MAIN", "FINAL")
                .count();
        assertEquals(0, medalMatchesPremature, "1/4 决赛完成后不应直接生成金牌赛/铜牌赛");

        List<Match> semis = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tid)
                .eq(Match::getPhaseCode, "MAIN")
                .eq(Match::getRound, 2)
                .orderByAsc(Match::getKnockoutBracketSlot)
                .list();
        assertEquals(2, semis.size(), "1/4 决赛完成后应先生成半决赛");
        for (Match m : semis) {
            acceptKoWithWinner(host, m, true);
        }

        long medalMatches = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tid)
                .eq(Match::getRound, 1)
                .in(Match::getPhaseCode, "MAIN", "FINAL")
                .count();
        assertEquals(2, medalMatches, "半决赛完成后才应生成金牌赛/铜牌赛");
    }

    @Test
    @DisplayName("1/4挂载资格赛：按赛果分步结算积分（资格赛落败→1/4落败→铜牌→金牌）")
    void mountedQualifierPointsAwardedStepByStep() {
        User host = createHost();
        List<User> players = createPlayers(12, "kqp");
        long tid = setupGroupTournament(host, 12, 6);
        registerAll(tid, players);
        closeRegistration(host, tid);

        TournamentCompetitionConfig cfgPatch = new TournamentCompetitionConfig();
        cfgPatch.setTournamentId(tid);
        cfgPatch.setKnockoutStartRound(4);
        cfgPatch.setQualifierRound(4);
        competitionService.saveConfig(host, cfgPatch);

        List<TournamentGroup> groups = tournamentGroupMapper.selectList(
                Wrappers.<TournamentGroup>lambdaQuery()
                        .eq(TournamentGroup::getTournamentId, tid)
                        .orderByAsc(TournamentGroup::getGroupOrder));
        assertEquals(2, groups.size());
        Map<Long, List<Long>> roster = new LinkedHashMap<>();
        roster.put(groups.get(0).getId(), players.subList(0, 6).stream().map(User::getId).toList());
        roster.put(groups.get(1).getId(), players.subList(6, 12).stream().map(User::getId).toList());
        competitionService.saveGroupMembers(host, tid, roster);
        competitionService.generateGroupMatches(host, tid);
        lockAllGroupMatches(host, tid);

        knockoutBracketService.generateFirstKnockoutRound(host, tid);
        List<Match> koQualifiers = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tid)
                .eq(Match::getPhaseCode, "QUALIFIER")
                .eq(Match::getCreateSource, KnockoutBracketService.SOURCE_AUTO_FROM_GROUP_KO_QUALIFIER)
                .orderByAsc(Match::getId)
                .list();
        assertEquals(2, koQualifiers.size());

        // Step 1: 仅锁定一场挂载资格赛 -> 只应新增1个非空积分（该场资格赛落败者）
        acceptKoWithWinner(host, koQualifiers.get(0), true);
        long awardedAfterQualifier = userTournamentPointsService.lambdaQuery()
                .eq(UserTournamentPoints::getTournamentId, tid)
                .isNotNull(UserTournamentPoints::getPoints)
                .count();
        assertTrue(awardedAfterQualifier >= 1, "挂载资格赛落败后应先导出其积分");

        // Step 2: 完成全部1/4决赛 -> 应能导出第5~8名（总的非空积分条目继续增加）
        List<Match> quarterFinals = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tid)
                .eq(Match::getPhaseCode, "MAIN")
                .eq(Match::getRound, 4)
                .orderByAsc(Match::getKnockoutBracketSlot)
                .list();
        assertEquals(4, quarterFinals.size());
        for (Match m : quarterFinals) {
            // 若该槽位依赖未结束资格赛，先补锁资格赛
            if ((m.getPlayer1Id() == null || m.getPlayer2Id() == null)
                    && !Boolean.TRUE.equals(koQualifiers.get(1).getResultLocked())) {
                acceptKoWithWinner(host, koQualifiers.get(1), true);
                m = matchService.getById(m.getId());
            }
            acceptKoWithWinner(host, m, true);
        }
        long awardedAfterQuarter = userTournamentPointsService.lambdaQuery()
                .eq(UserTournamentPoints::getTournamentId, tid)
                .isNotNull(UserTournamentPoints::getPoints)
                .count();
        assertTrue(awardedAfterQuarter > awardedAfterQualifier, "1/4决赛完成后应继续新增第5~8名积分");

        // Step 3: 半决赛完成会生成奖牌赛，先锁铜牌赛 -> 应继续增加已导出积分
        List<Match> semis = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tid)
                .eq(Match::getPhaseCode, "MAIN")
                .eq(Match::getRound, 2)
                .orderByAsc(Match::getKnockoutBracketSlot)
                .list();
        assertEquals(2, semis.size());
        for (Match m : semis) {
            acceptKoWithWinner(host, m, true);
        }
        Match bronze = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tid)
                .eq(Match::getRound, 1)
                .eq(Match::getPhaseCode, "MAIN")
                .like(Match::getCategory, "铜")
                .last("LIMIT 1")
                .one();
        assertTrue(bronze != null, "应生成铜牌赛");
        acceptKoWithWinner(host, bronze, true);
        long awardedAfterBronze = userTournamentPointsService.lambdaQuery()
                .eq(UserTournamentPoints::getTournamentId, tid)
                .isNotNull(UserTournamentPoints::getPoints)
                .count();
        assertTrue(awardedAfterBronze >= awardedAfterQuarter, "铜牌赛后应可导出第3/4名积分");

        // Step 4: 锁定金牌赛 -> 全部12人应都有积分
        Match gold = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tid)
                .eq(Match::getRound, 1)
                .eq(Match::getPhaseCode, "FINAL")
                .like(Match::getCategory, "金牌")
                .last("LIMIT 1")
                .one();
        assertTrue(gold != null, "应生成金牌赛");
        acceptKoWithWinner(host, gold, true);

        long awardedFinal = userTournamentPointsService.lambdaQuery()
                .eq(UserTournamentPoints::getTournamentId, tid)
                .isNotNull(UserTournamentPoints::getPoints)
                .count();
        assertEquals(12L, awardedFinal, "金牌赛结束后应完成第1~12名积分");
    }

    @Test
    @DisplayName("8人2组：手动排签首轮默认模式0，且记录操作者与来源")
    void manualFirstRoundDraftAndGenerate() {
        User host = createHost();
        List<User> players = createPlayers(8, "k3");
        long tid = setupGroupTournament(host, 8, 4);
        registerAll(tid, players);
        closeRegistration(host, tid);

        List<TournamentGroup> groups = tournamentGroupMapper.selectList(
                Wrappers.<TournamentGroup>lambdaQuery()
                        .eq(TournamentGroup::getTournamentId, tid)
                        .orderByAsc(TournamentGroup::getGroupOrder));
        Map<Long, List<Long>> roster = new LinkedHashMap<>();
        roster.put(groups.get(0).getId(), players.subList(0, 4).stream().map(User::getId).toList());
        roster.put(groups.get(1).getId(), players.subList(4, 8).stream().map(User::getId).toList());
        competitionService.saveGroupMembers(host, tid, roster);
        competitionService.generateGroupMatches(host, tid);
        lockAllGroupMatches(host, tid);

        List<KnockoutBracketService.ManualPairDraft> draft = knockoutBracketService.buildManualFirstRoundDraft(host, tid);
        assertEquals(4, draft.size());
        List<KnockoutBracketService.ManualPairInput> picked = draft.stream()
                .map(x -> new KnockoutBracketService.ManualPairInput(x.defaultPlayer1Id, x.defaultPlayer2Id))
                .toList();

        int n = knockoutBracketService.generateFirstKnockoutRoundManual(host, tid, picked);
        assertEquals(4, n);

        List<Match> r8 = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tid)
                .eq(Match::getPhaseCode, "MAIN")
                .eq(Match::getRound, 8)
                .orderByAsc(Match::getKnockoutBracketSlot)
                .list();
        assertEquals(4, r8.size());
        for (Match m : r8) {
            assertTrue(m.getCategory() != null && m.getCategory().startsWith("[手动排签]"));
            assertEquals(host.getId(), m.getCreatedByUserId());
            assertEquals(KnockoutBracketService.SOURCE_MANUAL_KO_EDITOR, m.getCreateSource());
        }
    }

    @Test
    @DisplayName("8人2组：手动排签不允许重复选手")
    void manualFirstRoundRejectDuplicate() {
        User host = createHost();
        List<User> players = createPlayers(8, "k4");
        long tid = setupGroupTournament(host, 8, 4);
        registerAll(tid, players);
        closeRegistration(host, tid);

        List<TournamentGroup> groups = tournamentGroupMapper.selectList(
                Wrappers.<TournamentGroup>lambdaQuery()
                        .eq(TournamentGroup::getTournamentId, tid)
                        .orderByAsc(TournamentGroup::getGroupOrder));
        Map<Long, List<Long>> roster = new LinkedHashMap<>();
        roster.put(groups.get(0).getId(), players.subList(0, 4).stream().map(User::getId).toList());
        roster.put(groups.get(1).getId(), players.subList(4, 8).stream().map(User::getId).toList());
        competitionService.saveGroupMembers(host, tid, roster);
        competitionService.generateGroupMatches(host, tid);
        lockAllGroupMatches(host, tid);

        List<KnockoutBracketService.ManualPairDraft> draft = knockoutBracketService.buildManualFirstRoundDraft(host, tid);
        List<KnockoutBracketService.ManualPairInput> bad = new ArrayList<>();
        for (KnockoutBracketService.ManualPairDraft d : draft) {
            bad.add(new KnockoutBracketService.ManualPairInput(d.defaultPlayer1Id, d.defaultPlayer2Id));
        }
        bad.set(1, new KnockoutBracketService.ManualPairInput(draft.get(0).defaultPlayer1Id, draft.get(1).defaultPlayer2Id));

        assertThrows(IllegalStateException.class,
                () -> knockoutBracketService.generateFirstKnockoutRoundManual(host, tid, bad));
    }

    @Test
    @DisplayName("手动生成下一轮：可清理已错误生成的后续轮次并重建")
    void manualGenerateNextRound_clearsDownstreamAndRegenerates() {
        User host = createHost();
        List<User> players = createPlayers(8, "k5");
        long tid = setupGroupTournament(host, 8, 4);
        registerAll(tid, players);
        closeRegistration(host, tid);

        List<TournamentGroup> groups = tournamentGroupMapper.selectList(
                Wrappers.<TournamentGroup>lambdaQuery()
                        .eq(TournamentGroup::getTournamentId, tid)
                        .orderByAsc(TournamentGroup::getGroupOrder));
        Map<Long, List<Long>> roster = new LinkedHashMap<>();
        roster.put(groups.get(0).getId(), players.subList(0, 4).stream().map(User::getId).toList());
        roster.put(groups.get(1).getId(), players.subList(4, 8).stream().map(User::getId).toList());
        competitionService.saveGroupMembers(host, tid, roster);
        competitionService.generateGroupMatches(host, tid);
        lockAllGroupMatches(host, tid);

        knockoutBracketService.generateFirstKnockoutRound(host, tid);
        List<Match> r8 = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tid)
                .eq(Match::getPhaseCode, "MAIN")
                .eq(Match::getRound, 8)
                .orderByAsc(Match::getKnockoutBracketSlot)
                .list();
        for (Match m : r8) {
            acceptKoWithWinner(host, m, true);
        }
        List<Match> semis = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tid)
                .eq(Match::getPhaseCode, "MAIN")
                .eq(Match::getRound, 4)
                .orderByAsc(Match::getKnockoutBracketSlot)
                .list();
        for (Match m : semis) {
            acceptKoWithWinner(host, m, true);
        }

        List<Match> oldFinalAndBronze = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tid)
                .eq(Match::getRound, 1)
                .in(Match::getPhaseCode, "MAIN", "FINAL")
                .list();
        assertEquals(2, oldFinalAndBronze.size());
        Set<Long> oldIds = oldFinalAndBronze.stream().map(Match::getId).collect(java.util.stream.Collectors.toSet());

        int added = knockoutBracketService.generateNextKnockoutRound(host, tid);
        assertEquals(2, added);

        List<Match> rebuiltFinalAndBronze = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tid)
                .eq(Match::getRound, 1)
                .in(Match::getPhaseCode, "MAIN", "FINAL")
                .list();
        assertEquals(2, rebuiltFinalAndBronze.size());
        for (Match m : rebuiltFinalAndBronze) {
            assertTrue(!oldIds.contains(m.getId()), "应清理后续已生成轮次并重建新场次");
            assertTrue(!Boolean.TRUE.equals(m.getResultLocked()), "重建后的场次应为未验收状态");
        }
    }

    @Test
    @DisplayName("手动生成下一轮：当前轮次未全部验收时应失败且不清除后续轮次")
    void manualGenerateNextRound_requiresCurrentRoundAllLocked() {
        User host = createHost();
        List<User> players = createPlayers(8, "k6");
        long tid = setupGroupTournament(host, 8, 4);
        registerAll(tid, players);
        closeRegistration(host, tid);

        List<TournamentGroup> groups = tournamentGroupMapper.selectList(
                Wrappers.<TournamentGroup>lambdaQuery()
                        .eq(TournamentGroup::getTournamentId, tid)
                        .orderByAsc(TournamentGroup::getGroupOrder));
        Map<Long, List<Long>> roster = new LinkedHashMap<>();
        roster.put(groups.get(0).getId(), players.subList(0, 4).stream().map(User::getId).toList());
        roster.put(groups.get(1).getId(), players.subList(4, 8).stream().map(User::getId).toList());
        competitionService.saveGroupMembers(host, tid, roster);
        competitionService.generateGroupMatches(host, tid);
        lockAllGroupMatches(host, tid);

        knockoutBracketService.generateFirstKnockoutRound(host, tid);
        List<Match> r8 = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tid)
                .eq(Match::getPhaseCode, "MAIN")
                .eq(Match::getRound, 8)
                .orderByAsc(Match::getKnockoutBracketSlot)
                .list();
        for (Match m : r8) {
            acceptKoWithWinner(host, m, true);
        }
        List<Match> semis = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tid)
                .eq(Match::getPhaseCode, "MAIN")
                .eq(Match::getRound, 4)
                .orderByAsc(Match::getKnockoutBracketSlot)
                .list();
        assertEquals(2, semis.size());
        // 仅锁定一场半决赛，制造“当前轮次未全部验收”
        acceptKoWithWinner(host, semis.get(0), true);

        List<Match> finalsBefore = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tid)
                .eq(Match::getRound, 1)
                .in(Match::getPhaseCode, "MAIN", "FINAL")
                .list();
        assertEquals(0, finalsBefore.size(), "未全验收前不应有决赛/铜牌赛");

        assertThrows(IllegalStateException.class, () -> knockoutBracketService.generateNextKnockoutRound(host, tid));

        List<Match> finalsAfter = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tid)
                .eq(Match::getRound, 1)
                .in(Match::getPhaseCode, "MAIN", "FINAL")
                .list();
        assertEquals(0, finalsAfter.size(), "失败时不应清除/新增后续轮次");
    }

    @Test
    @DisplayName("手动生成下一轮：无淘汰赛时应提示先生成首轮")
    void manualGenerateNextRound_noKnockoutMatches() {
        User host = createHost();
        List<User> players = createPlayers(8, "k7");
        long tid = setupGroupTournament(host, 8, 4);
        registerAll(tid, players);
        closeRegistration(host, tid);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> knockoutBracketService.generateNextKnockoutRound(host, tid));
        assertTrue(ex.getMessage().contains("先生成首轮"));
    }

    @Test
    @DisplayName("手动生成下一轮：当前轮次未锁且存在错误后续轮次时，不应清除后续")
    void manualGenerateNextRound_unlockedCurrentRound_keepsWrongDownstream() {
        User host = createHost();
        List<User> players = createPlayers(8, "k8");
        long tid = setupGroupTournament(host, 8, 4);
        registerAll(tid, players);
        closeRegistration(host, tid);

        List<TournamentGroup> groups = tournamentGroupMapper.selectList(
                Wrappers.<TournamentGroup>lambdaQuery()
                        .eq(TournamentGroup::getTournamentId, tid)
                        .orderByAsc(TournamentGroup::getGroupOrder));
        Map<Long, List<Long>> roster = new LinkedHashMap<>();
        roster.put(groups.get(0).getId(), players.subList(0, 4).stream().map(User::getId).toList());
        roster.put(groups.get(1).getId(), players.subList(4, 8).stream().map(User::getId).toList());
        competitionService.saveGroupMembers(host, tid, roster);
        competitionService.generateGroupMatches(host, tid);
        lockAllGroupMatches(host, tid);

        knockoutBracketService.generateFirstKnockoutRound(host, tid);
        List<Match> r8 = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tid)
                .eq(Match::getPhaseCode, "MAIN")
                .eq(Match::getRound, 8)
                .orderByAsc(Match::getKnockoutBracketSlot)
                .list();
        for (Match m : r8) {
            acceptKoWithWinner(host, m, true);
        }
        List<Match> semis = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tid)
                .eq(Match::getPhaseCode, "MAIN")
                .eq(Match::getRound, 4)
                .orderByAsc(Match::getKnockoutBracketSlot)
                .list();
        assertEquals(2, semis.size());
        acceptKoWithWinner(host, semis.get(0), true); // 当前轮次未全部锁定

        Match wrongFinal = insertFakeKoMatch(tid, 1, 0, "MAIN", players.get(0).getId(), players.get(1).getId());
        Long wrongFinalId = wrongFinal.getId();

        assertThrows(IllegalStateException.class, () -> knockoutBracketService.generateNextKnockoutRound(host, tid));
        Match stillThere = matchService.getById(wrongFinalId);
        assertTrue(stillThere != null, "当前轮次未全验收时，不应清理后续错误轮次");
    }

    /** 正赛/决赛须双方选手 + 办赛方（staff）验收后方可锁定并触发晋级链 */
    private void acceptKoWithWinner(User host, Match m, boolean player1Wins) {
        List<String> s1 = player1Wins ? WIN_SCORES : LOSE_SCORES;
        List<String> s2 = player1Wins ? LOSE_SCORES : WIN_SCORES;
        competitionService.saveMatchScore(host, m.getId(), 1, s1, s2, false, null, false);
        competitionService.acceptMatchScore(userService.getById(m.getPlayer1Id()), m.getId(), "ko-it");
        competitionService.acceptMatchScore(userService.getById(m.getPlayer2Id()), m.getId(), "ko-it");
        competitionService.acceptMatchScore(host, m.getId(), "ko-host");
    }

    private long setupGroupTournament(User host, int participantCount, int groupSize) {
        TournamentLevel lvl = new TournamentLevel();
        lvl.setCode(uniq("KLVL"));
        lvl.setName("KO-IT");
        lvl.setDefaultChampionRatio(new BigDecimal("10.00"));
        lvl.setDefaultBottomPoints(1);
        tournamentLevelService.save(lvl);

        Season season = new Season();
        season.setYear(2097);
        season.setHalf(2);
        seasonService.save(season);

        Series series = new Series();
        series.setSeasonId(season.getId());
        series.setSequence(1);
        series.setName("淘汰赛集成系列");
        seriesService.save(series);

        Tournament t = new Tournament();
        t.setSeriesId(series.getId());
        t.setLevelCode(lvl.getCode());
        t.setHostUserId(host.getId());
        t.setChampionPointsRatio(new BigDecimal("10.00"));
        t.setStatus(0);
        tournamentService.save(t);
        long tid = t.getId();

        TournamentRegistrationSetting reg = new TournamentRegistrationSetting();
        reg.setTournamentId(tid);
        reg.setEnabled(true);
        reg.setDeadline(LocalDateTime.now().plusDays(1));
        reg.setQuotaN(32);
        reg.setMode(0);
        registrationService.saveSetting(host, reg);

        TournamentCompetitionConfig cfg = new TournamentCompetitionConfig();
        cfg.setTournamentId(tid);
        cfg.setParticipantCount(participantCount);
        cfg.setEntryMode(0);
        cfg.setMatchMode(3);
        cfg.setGroupMode(1);
        cfg.setGroupSize(groupSize);
        cfg.setGroupAllowDraw(true);
        cfg.setGroupStageSets(8);
        cfg.setKnockoutStageSets(8);
        cfg.setFinalStageSets(10);
        cfg.setKnockoutStartRound(8);
        cfg.setKnockoutBracketMode(0);
        cfg.setKnockoutAutoFromGroup(false);
        cfg.setQualifierRound(null);
        cfg.setGroupStageDeadline(LocalDateTime.now().minusHours(1));
        competitionService.saveConfig(host, cfg);
        return tid;
    }

    private void registerAll(long tournamentId, List<User> players) {
        LocalDateTime base = LocalDateTime.now();
        for (int i = 0; i < players.size(); i++) {
            registrationService.register(tournamentId, players.get(i).getId(), base.plusNanos(i + 1L));
        }
    }

    private void closeRegistration(User host, long tournamentId) {
        TournamentRegistrationSetting patch = new TournamentRegistrationSetting();
        patch.setTournamentId(tournamentId);
        patch.setDeadline(LocalDateTime.now().minusDays(1));
        registrationService.saveSetting(host, patch);
    }

    private void lockAllGroupMatches(User host, long tournamentId) {
        for (Match m : matchService.lambdaQuery().eq(Match::getTournamentId, tournamentId).eq(Match::getPhaseCode, "GROUP").list()) {
            boolean p1Wins = m.getPlayer1Id() != null && m.getPlayer1Id() < m.getPlayer2Id();
            List<String> s1 = p1Wins ? WIN_SCORES : LOSE_SCORES;
            List<String> s2 = p1Wins ? LOSE_SCORES : WIN_SCORES;
            competitionService.saveMatchScore(host, m.getId(), 1, s1, s2, false, null, false);
            competitionService.acceptMatchScore(userService.getById(m.getPlayer1Id()), m.getId(), "it");
            competitionService.acceptMatchScore(userService.getById(m.getPlayer2Id()), m.getId(), "it");
        }
    }

    private Match insertFakeKoMatch(long tournamentId, int round, int slot, String phaseCode, Long p1, Long p2) {
        Match m = new Match();
        m.setTournamentId(tournamentId);
        m.setPhaseCode(phaseCode);
        m.setRound(round);
        m.setKnockoutBracketSlot(slot);
        m.setCategory("FAKE-KO");
        m.setPlayer1Id(p1);
        m.setPlayer2Id(p2);
        m.setStatus((byte) 0);
        m.setResultLocked(false);
        m.setCreatedAt(LocalDateTime.now());
        m.setUpdatedAt(LocalDateTime.now());
        matchService.save(m);
        return m;
    }
}
