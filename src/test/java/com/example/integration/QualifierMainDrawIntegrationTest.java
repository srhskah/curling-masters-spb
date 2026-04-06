package com.example.integration;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.demo.DemoApplication;
import com.example.dto.DrawPool;
import com.example.entity.*;
import com.example.mapper.TournamentDrawResultMapper;
import com.example.mapper.TournamentGroupMemberMapper;
import com.example.service.*;
import com.example.service.impl.DrawManagementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 正赛+资格赛：直通车三种抽签 → 导入 MAIN 席 → 资格赛池（默认随机）→ 导入资格赛席 → 生成小组赛。
 * 数据源说明见 {@link DrawFullFlowIntegrationTest}。
 */
@SpringBootTest(classes = DemoApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("draw-it")
@Transactional
@Tag("integration")
class QualifierMainDrawIntegrationTest {

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
            synchronized (QualifierMainDrawIntegrationTest.class) {
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
                                "未配置 DRAW_IT_JDBC_URL，且 Testcontainers 无法启动 MySQL。"
                                        + " 原因: " + ex.getMessage(),
                                ex);
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
    @Autowired private DrawManagementService drawManagementService;
    @Autowired private IMatchService matchService;
    @Autowired private TournamentDrawResultMapper drawResultMapper;
    @Autowired private TournamentGroupMemberMapper groupMemberMapper;
    @Autowired private TournamentEntryService tournamentEntryService;

    /** 4 组×3 人（未满编）单循环：4×3=12 场 */
    private static final int EXPECTED_GROUP_MATCHES_PARTIAL = 12;

    private String uniq(String prefix) {
        return prefix + NAME_SEQ.incrementAndGet();
    }

    private User createHost() {
        return userService.register(uniq("qh_"), "pw123456", uniq("qh") + "@t.com");
    }

    private List<User> createPlayers(int n, String pfx) {
        List<User> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(userService.register(uniq(pfx + "_"), "pw123456", uniq("qp") + i + "@t.com"));
        }
        return list;
    }

    private long setupQualifierTournament(User host) {
        TournamentLevel lvl = new TournamentLevel();
        lvl.setCode(uniq("QLVL"));
        lvl.setName("资格赛IT");
        lvl.setDefaultChampionRatio(new BigDecimal("10.00"));
        lvl.setDefaultBottomPoints(1);
        tournamentLevelService.save(lvl);

        Season season = new Season();
        season.setYear(2098);
        season.setHalf(2);
        seasonService.save(season);

        Series series = new Series();
        series.setSeasonId(season.getId());
        series.setSequence(1);
        series.setName("资格赛集成系列");
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
        reg.setQuotaN(16);
        reg.setMode(1);
        reg.setMainDirectM(8);
        reg.setQualifierSeedCount(4);
        registrationService.saveSetting(host, reg);

        TournamentCompetitionConfig cfg = new TournamentCompetitionConfig();
        cfg.setTournamentId(tid);
        cfg.setParticipantCount(16);
        cfg.setEntryMode(1);
        cfg.setKnockoutQualifyCount(4);
        cfg.setQualifierSets(8);
        cfg.setMatchMode(3);
        cfg.setGroupMode(1);
        cfg.setGroupSize(4);
        cfg.setGroupAllowDraw(true);
        cfg.setGroupStageSets(8);
        cfg.setKnockoutStageSets(8);
        cfg.setFinalStageSets(10);
        competitionService.saveConfig(host, cfg);

        return tid;
    }

    private void registerAll(long tournamentId, List<User> players) {
        LocalDateTime base = LocalDateTime.now();
        for (int i = 0; i < players.size(); i++) {
            registrationService.register(tournamentId, players.get(i).getId(), base.plusNanos(i + 1L));
        }
    }

    private void closeRegistrationPastDeadline(User host, long tournamentId) {
        TournamentRegistrationSetting patch = new TournamentRegistrationSetting();
        patch.setTournamentId(tournamentId);
        patch.setDeadline(LocalDateTime.now().minusDays(1));
        registrationService.saveSetting(host, patch);
    }

    private void runOneTouchDraw(long tournamentId, DrawPool pool) {
        Map<String, Object> cfg = drawManagementService.getDrawConfig(tournamentId, pool);
        @SuppressWarnings("unchecked")
        List<Long> parts = (List<Long>) cfg.get("drawParticipants");
        assertFalse(parts == null || parts.isEmpty(), "抽签名单为空 pool=" + pool);
        Long first = parts.getFirst();
        List<Long> eligible = drawManagementService.getEligibleGroupIds(tournamentId, first, pool);
        assertFalse(eligible.isEmpty(), "首签选手无可用小组");
        drawManagementService.performDraw(tournamentId, first, eligible.getFirst(), pool);
        long drawn = drawResultMapper.selectCount(
                Wrappers.<TournamentDrawResult>lambdaQuery()
                        .eq(TournamentDrawResult::getTournamentId, tournamentId)
                        .eq(TournamentDrawResult::getDrawPool, pool.name()));
        assertEquals(parts.size(), drawn, "一次抽签后应全员落位 pool=" + pool);
    }

    @ParameterizedTest(name = "MAIN={0} → 资格赛随机 → 小组赛")
    @ValueSource(strings = {"RANDOM", "TIERED", "SEED"})
    @DisplayName("正赛+资格赛：直通车抽签（三种）后导入，资格赛池抽签导入，再生成小组赛")
    void qualifierFlow_afterMainDrawThreeKinds(String mainDrawType) {
        User host = createHost();
        List<User> players = createPlayers(12, "qm");
        long tid = setupQualifierTournament(host);
        registerAll(tid, players);
        closeRegistrationPastDeadline(host, tid);

        registrationService.materializeMainDirectEntries(host, tid, LocalDateTime.now());

        for (int i = 8; i < 12; i++) {
            Long uid = players.get(i).getId();
            if (tournamentEntryService.lambdaQuery()
                    .eq(TournamentEntry::getTournamentId, tid)
                    .eq(TournamentEntry::getUserId, uid)
                    .count() == 0) {
                TournamentEntry e = new TournamentEntry();
                e.setTournamentId(tid);
                e.setUserId(uid);
                e.setEntryType(2);
                e.setCreatedAt(LocalDateTime.now());
                tournamentEntryService.save(e);
            }
        }

        int groupCount = 4;
        if ("RANDOM".equals(mainDrawType)) {
            drawManagementService.initializeDraw(tid, "RANDOM", groupCount, null, null, DrawPool.MAIN);
        } else if ("TIERED".equals(mainDrawType)) {
            drawManagementService.initializeDraw(tid, "TIERED", groupCount, 2, null, DrawPool.MAIN);
        } else {
            drawManagementService.initializeDraw(tid, "SEED", groupCount, null, 4, DrawPool.MAIN);
        }
        runOneTouchDraw(tid, DrawPool.MAIN);
        int mainImported = drawManagementService.importDrawResultsToGroups(tid, DrawPool.MAIN);
        assertEquals(8, mainImported);

        drawManagementService.initializeDraw(tid, "RANDOM", groupCount, null, null, DrawPool.QUALIFIER);
        runOneTouchDraw(tid, DrawPool.QUALIFIER);
        int qImported = drawManagementService.importDrawResultsToGroups(tid, DrawPool.QUALIFIER);
        assertEquals(4, qImported);

        long members = groupMemberMapper.selectCount(
                Wrappers.<TournamentGroupMember>lambdaQuery()
                        .eq(TournamentGroupMember::getTournamentId, tid));
        assertEquals(12, members);

        competitionService.generateGroupMatches(host, tid);
        long groupMatches = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tid)
                .eq(Match::getPhaseCode, "GROUP")
                .count();
        assertEquals(EXPECTED_GROUP_MATCHES_PARTIAL, groupMatches);
    }
}
