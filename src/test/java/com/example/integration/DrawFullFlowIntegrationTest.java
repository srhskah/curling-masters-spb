package com.example.integration;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.entity.*;
import com.example.mapper.TournamentDrawResultMapper;
import com.example.mapper.TournamentGroupMemberMapper;
import com.example.demo.DemoApplication;
import com.example.service.*;
import com.example.service.impl.DrawManagementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 集成测试：多用户报名（截止后视同通过）→ 初始化抽签 → 每名选手独立点签 →
 * 导入小组名单 → 生成小组赛对阵。
 * <p>
 * <strong>数据源（二选一）</strong>
 * <ol>
 *   <li><strong>推荐（WSL2 / Testcontainers 探测 Docker 失败时）</strong>：指定本机已运行的 MySQL（如 docker-compose 映射的 12306），使用<strong>独立库名</strong>，勿与业务库共用。
 *   <pre>{@code
 *   export DRAW_IT_JDBC_URL='jdbc:mysql://127.0.0.1:12306/draw_it_integration?serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true'
 *   export DRAW_IT_DB_USER=root
 *   export DRAW_IT_DB_PASSWORD='与 .env 中 MYSQL_ROOT_PASSWORD 一致'
 *   ./gradlew --no-daemon integrationTest
 *   }</pre>
 *   若 Gradle 守护进程已在无上述变量的环境中启动，请 {@code ./gradlew --stop} 后再 export 并执行，或始终加 {@code --no-daemon}。
 *   </li>
 *   <li>未设置 {@code DRAW_IT_JDBC_URL} 时：若执行的是 {@code ./gradlew integrationTest} 且项目根目录 {@code .env} 中有
 *   {@code MYSQL_ROOT_PASSWORD}，Gradle 会注入连 {@code 127.0.0.1:12306} 的默认 URL（与 docker-compose 中 MySQL 映射一致，库名 {@code draw_it_integration}）。</li>
 *   <li>以上皆无：再尝试 Testcontainers 启动 MySQL（需 JVM 能访问 Docker API）。</li>
 * </ol>
 */
@SpringBootTest(classes = DemoApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("draw-it")
@Transactional
@Tag("integration")
class DrawFullFlowIntegrationTest {

    private static final AtomicLong NAME_SEQ = new AtomicLong();

    /** 仅在不使用 DRAW_IT_JDBC_URL 时懒启动 */
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
            synchronized (DrawFullFlowIntegrationTest.class) {
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
                                "未配置 DRAW_IT_JDBC_URL，且 Testcontainers 无法启动 MySQL（通常因 JVM 探测不到 Docker）。"
                                        + " 请任选其一：① 在 shell 中 export DRAW_IT_JDBC_URL（及 DRAW_IT_DB_USER / DRAW_IT_DB_PASSWORD）；"
                                        + " ② 在项目根 .env 填写 MYSQL_ROOT_PASSWORD 后执行 ./gradlew integrationTest（将自动连 127.0.0.1:12306）。"
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

    /** 24 人、4 组×6 人：单循环组内共 4×C(6,2)=60 场 */
    private static final int EXPECTED_GROUP_MATCHES_24 = 60;
    /** 36 人、6 组×6 人 */
    private static final int EXPECTED_GROUP_MATCHES_36 = 90;

    private String uniq(String prefix) {
        return prefix + NAME_SEQ.incrementAndGet();
    }

    private User createHost() {
        return userService.register(uniq("host_"), "pw123456", uniq("h") + "@t.com");
    }

    private List<User> createPlayers(int n, String pfx) {
        List<User> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(userService.register(uniq(pfx + "_"), "pw123456", uniq("p") + i + "@t.com"));
        }
        return list;
    }

    private long setupTournament(User host, int participantCount, int groupSize) {
        TournamentLevel lvl = new TournamentLevel();
        lvl.setCode(uniq("LVL"));
        lvl.setName("IT等级");
        lvl.setDefaultChampionRatio(new BigDecimal("10.00"));
        lvl.setDefaultBottomPoints(1);
        tournamentLevelService.save(lvl);

        Season season = new Season();
        season.setYear(2099);
        season.setHalf(1);
        seasonService.save(season);

        Series series = new Series();
        series.setSeasonId(season.getId());
        series.setSequence(1);
        series.setName("集成测试系列");
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
        // 须在「未截止」窗口内才能 register()；截止改到过去在 closeRegistrationPastDeadline 中做
        reg.setDeadline(LocalDateTime.now().plusDays(1));
        reg.setQuotaN(Math.max(participantCount + 8, 32));
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
        competitionService.saveConfig(host, cfg);

        return tid;
    }

    /**
     * 每条报名使用不同 {@code registeredAt}，避免 listRows 排序在「同时间戳」下不稳定，
     * 导致 getEligibleGroupIds 与 performDraw 内两次 getDrawConfig 顺序不一致而抽签失败。
     */
    private void registerAll(long tournamentId, List<User> players) {
        LocalDateTime base = LocalDateTime.now();
        for (int i = 0; i < players.size(); i++) {
            registrationService.register(tournamentId, players.get(i).getId(), base.plusNanos(i + 1L));
        }
    }

    /** 模拟报名已截止（待审在截止后视同通过，且抽签可立即开放） */
    private void closeRegistrationPastDeadline(User host, long tournamentId) {
        TournamentRegistrationSetting patch = new TournamentRegistrationSetting();
        patch.setTournamentId(tournamentId);
        patch.setDeadline(LocalDateTime.now().minusDays(1));
        registrationService.saveSetting(host, patch);
    }

    private void runOneTouchDraw(long tournamentId) {
        Map<String, Object> cfg = drawManagementService.getDrawConfig(tournamentId);
        @SuppressWarnings("unchecked")
        List<Long> parts = (List<Long>) cfg.get("drawParticipants");
        assertFalse(parts == null || parts.isEmpty(), "抽签名单为空");
        Map<String, Object> status = drawManagementService.getDrawStatus(tournamentId);
        TournamentDraw draw = (TournamentDraw) status.get("draw");
        assertNotNull(draw);
        for (Long uid : parts) {
            if ("RANDOM".equals(draw.getDrawType())) {
                drawManagementService.performDraw(tournamentId, uid, null);
            } else {
                List<Long> eligible = drawManagementService.getEligibleGroupIds(tournamentId, uid);
                assertFalse(eligible.isEmpty(), "选手无可用小组 uid=" + uid);
                drawManagementService.performDraw(tournamentId, uid, eligible.getFirst());
            }
        }
        long drawn = drawResultMapper.selectCount(
                Wrappers.<TournamentDrawResult>lambdaQuery()
                        .eq(TournamentDrawResult::getTournamentId, tournamentId));
        assertEquals(parts.size(), drawn, "抽签完成后应全员落位");
    }

    private void importAndGenerate(User host, long tournamentId, int expectedPlayers, int expectedGroupMatches) {
        int n = drawManagementService.importDrawResultsToGroups(tournamentId);
        assertEquals(expectedPlayers, n);
        competitionService.generateGroupMatches(host, tournamentId);
        long members = groupMemberMapper.selectCount(
                Wrappers.<TournamentGroupMember>lambdaQuery()
                        .eq(TournamentGroupMember::getTournamentId, tournamentId));
        assertEquals(expectedPlayers, members);
        long groupMatches = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tournamentId)
                .eq(Match::getPhaseCode, "GROUP")
                .count();
        assertEquals(expectedGroupMatches, groupMatches);
    }

    @Test
    @DisplayName("默认抽签：24人 / 6人每组 / 4组")
    void randomDraw24() {
        User host = createHost();
        List<User> players = createPlayers(24, "pl");
        long tid = setupTournament(host, 24, 6);
        registerAll(tid, players);
        closeRegistrationPastDeadline(host, tid);
        drawManagementService.initializeDraw(tid, "RANDOM", 4, null, null);
        runOneTouchDraw(tid);
        importAndGenerate(host, tid, 24, EXPECTED_GROUP_MATCHES_24);
    }

    /**
     * 分档：档数须整除每组人数（≥2）。24人→4组×6人/组 → 可选 2、3、6 档。
     */
    @ParameterizedTest(name = "TIERED 24人 tierCount={0}")
    @ValueSource(ints = {2, 3, 6})
    @DisplayName("分档抽签（24人4组×6人）：档数 2 / 3 / 6")
    void tieredDraw24_validTierCounts(int tierCount) {
        User host = createHost();
        List<User> players = createPlayers(24, "pl");
        long tid = setupTournament(host, 24, 6);
        registerAll(tid, players);
        closeRegistrationPastDeadline(host, tid);
        drawManagementService.initializeDraw(tid, "TIERED", 4, tierCount, null);
        runOneTouchDraw(tid);
        importAndGenerate(host, tid, 24, EXPECTED_GROUP_MATCHES_24);
    }

    @ParameterizedTest(name = "TIERED 36人6组 tierCount={0}")
    @ValueSource(ints = {2, 3, 6})
    @DisplayName("分档抽签（36人6组）：覆盖 2 / 3 / 6 档")
    void tieredDraw36_sixGroups(int tierCount) {
        User host = createHost();
        List<User> players = createPlayers(36, "pl");
        long tid = setupTournament(host, 36, 6);
        registerAll(tid, players);
        closeRegistrationPastDeadline(host, tid);
        drawManagementService.initializeDraw(tid, "TIERED", 6, tierCount, null);
        runOneTouchDraw(tid);
        importAndGenerate(host, tid, 36, EXPECTED_GROUP_MATCHES_36);
    }

    @ParameterizedTest(name = "SEED 24人 seedCount={0}")
    @ValueSource(ints = {4, 8, 12, 16, 20})
    @DisplayName("种子抽签：前4/8/12/16/20 种子（均为4的倍数）")
    void seedDraw24(int seedCount) {
        User host = createHost();
        List<User> players = createPlayers(24, "pl");
        long tid = setupTournament(host, 24, 6);
        registerAll(tid, players);
        closeRegistrationPastDeadline(host, tid);
        drawManagementService.initializeDraw(tid, "SEED", 4, null, seedCount);
        runOneTouchDraw(tid);
        importAndGenerate(host, tid, 24, EXPECTED_GROUP_MATCHES_24);
    }
}
