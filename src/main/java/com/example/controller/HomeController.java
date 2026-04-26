package com.example.controller;

import com.example.util.CookieUtil;
import com.example.util.TimezoneUtil;
import com.example.entity.Season;
import com.example.entity.User;
import com.example.entity.UserTournamentPoints;
import com.example.entity.TournamentLevel;
import com.example.service.RankingService;
import com.example.service.SeasonService;
import com.example.service.ITournamentRegistrationService;
import com.example.service.INotificationService;
import com.example.service.SeriesService;
import com.example.service.TournamentService;
import com.example.service.UserService;
import com.example.service.UserTournamentPointsService;
import com.example.service.ITournamentLevelService;
import com.example.entity.Tournament;
import com.example.entity.Series;
import com.example.service.impl.TournamentRankingRosterService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.stream.Collectors;

@Controller
public class HomeController {

    private static final Logger logger = LoggerFactory.getLogger(HomeController.class);

    @Autowired private RankingService rankingService;
    @Autowired private SeasonService seasonService;
    @Autowired private ITournamentRegistrationService tournamentRegistrationService;
    @Autowired private SeriesService seriesService;
    @Autowired private TournamentService tournamentService;
    @Autowired private UserService userService;
    @Autowired private UserTournamentPointsService userTournamentPointsService;
    @Autowired private ITournamentLevelService tournamentLevelService;
    @Autowired private TournamentRankingRosterService tournamentRankingRosterService;
    @Autowired private INotificationService notificationService;

    @GetMapping("/")
    public String home(Authentication authentication, Model model, HttpServletRequest request) {
        logger.info("HomeController.home() called - Testing new log configuration");
        logger.info("Current timestamp for testing: {}", new java.util.Date());
        if (authentication != null) {
            logger.info("Authentication name: {}", authentication.getName());
            logger.info("Authentication principal: {}", authentication.getPrincipal());
            logger.info("Authentication authorities: {}", authentication.getAuthorities());
        }

        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            logger.info("Setting username to model: {}", username);
            model.addAttribute("username", username);
            model.addAttribute("authorities", authentication.getAuthorities());
            User me = userService.findByUsername(username);
            model.addAttribute("notificationUnreadCount", me == null ? 0L : notificationService.unreadCount(me.getId()));
        } else {
            logger.info("No authentication or not authenticated");
            model.addAttribute("notificationUnreadCount", 0L);
        }
        List<com.example.entity.NotificationMessage> homeN = notificationService.listPublishedForHome(20);
        List<com.example.entity.NotificationMessage> auto = homeN.stream()
                .filter(x -> x.getSourceType() != null && !x.getSourceType().isBlank())
                .toList();
        List<com.example.entity.NotificationMessage> manual = homeN.stream()
                .filter(x -> x.getSourceType() == null || x.getSourceType().isBlank())
                .toList();
        java.util.ArrayList<com.example.entity.NotificationMessage> picked = new java.util.ArrayList<>();
        if (!auto.isEmpty()) {
            picked.add(auto.get(0)); // 自动发送仅保留最新 1 条
        }
        for (com.example.entity.NotificationMessage x : manual) {
            if (picked.size() >= 3) break; // 首页总数最多 3 条
            picked.add(x);
        }
        model.addAttribute("homeNotifications", picked);
        
        // 从Cookie读取用户偏好设置
        String theme = CookieUtil.getCookie(request, "user_theme");
        String language = CookieUtil.getCookie(request, "user_language");
        String rememberMe = CookieUtil.getCookie(request, "remember_me");
        
        if (theme != null) {
            model.addAttribute("userTheme", theme);
        }
        if (language != null) {
            model.addAttribute("userLanguage", language);
        }
        if (rememberMe != null) {
            model.addAttribute("rememberMe", "true".equals(rememberMe));
        }
        
        // 读取登录信息
        String loginToken = CookieUtil.getCookie(request, "login_token");
        if (loginToken != null) {
            model.addAttribute("loginToken", loginToken);
        }
        
        // 添加系统统计信息
        addSystemStatistics(model);

        // 首页排名模块：总排名/当前赛季排名（仅前24）
        model.addAttribute("totalRankingTop24", rankingService.getTotalRanking(24));
        Season currentSeason = seasonService.lambdaQuery()
                .orderByDesc(Season::getYear)
                .orderByDesc(Season::getHalf)
                .last("LIMIT 1")
                .one();
        model.addAttribute("currentSeason", currentSeason);
        model.addAttribute("currentSeasonRankingTop24",
                currentSeason == null ? java.util.List.of() : rankingService.getSeasonRanking(currentSeason.getId(), 24));
        model.addAttribute("homeTotalMedalBoard", buildTotalMedalBoard());
        model.addAttribute("homeSeasonMedalBoard", buildSeasonMedalBoard(currentSeason));

        List<Tournament> openReg = tournamentRegistrationService.listOpenRegistrationTournaments(12, LocalDateTime.now());
        model.addAttribute("openRegistrationTournaments", openReg);
        Set<Long> openRegistrationTournamentIds = openReg.stream()
                .map(Tournament::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        model.addAttribute("openRegistrationTournamentIds", openRegistrationTournamentIds);

        // 首页入口：当前赛季 / 当前系列
        model.addAttribute("currentSeasonEntryUrl", currentSeason != null ? ("/season/detail/" + currentSeason.getId()) : null);
        model.addAttribute("currentSeasonEntryName", currentSeason != null
                ? (currentSeason.getYear() + "年" + (currentSeason.getHalf() == 1 ? "上半年" : "下半年"))
                : null);

        Series currentSeries = null;
        if (currentSeason != null && currentSeason.getId() != null) {
            currentSeries = seriesService.lambdaQuery()
                    .eq(Series::getSeasonId, currentSeason.getId())
                    .orderByDesc(Series::getSequence)
                    .last("LIMIT 1")
                    .one();
        }
        model.addAttribute("currentSeries", currentSeries);
        String currentSeriesName = null;
        if (currentSeries != null) {
            if (currentSeries.getName() != null && !currentSeries.getName().trim().isEmpty()) {
                currentSeriesName = currentSeries.getName().trim();
            } else if (currentSeries.getSequence() != null) {
                currentSeriesName = "第" + currentSeries.getSequence() + "系列";
            } else {
                currentSeriesName = "当前系列";
            }
        }
        model.addAttribute("currentSeriesName", currentSeriesName);
        model.addAttribute("currentSeriesEntryUrl", currentSeries != null ? ("/season/detail/by-series/" + currentSeries.getId()) : null);

        List<Tournament> currentSeriesOngoing = List.of();
        List<Tournament> currentSeriesRegistration = List.of();
        if (currentSeries != null && currentSeries.getId() != null) {
            currentSeriesOngoing = tournamentService.lambdaQuery()
                    .eq(Tournament::getSeriesId, currentSeries.getId())
                    .eq(Tournament::getStatus, 1)
                    .orderByAsc(Tournament::getStartDate)
                    .list();
            final Long csId = currentSeries.getId();
            currentSeriesRegistration = openReg.stream()
                    .filter(t -> Objects.equals(t.getSeriesId(), csId))
                    .sorted(Comparator.comparing(Tournament::getStartDate, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
        }
        model.addAttribute("currentSeriesOngoingTournaments", currentSeriesOngoing);
        model.addAttribute("currentSeriesRegistrationTournaments", currentSeriesRegistration);

        // 首页展示：任一赛事处于“筹备中/进行中”的系列（测试系列始终置顶并标注）
        List<Tournament> activeTournaments = tournamentService.lambdaQuery()
                .in(Tournament::getStatus, List.of(0, 1))
                .orderByDesc(Tournament::getStartDate)
                .list();
        List<Long> activeSeriesIds = activeTournaments.stream()
                .map(Tournament::getSeriesId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Series> activeSeriesById = activeSeriesIds.isEmpty()
                ? Map.of()
                : seriesService.listByIds(activeSeriesIds).stream().collect(Collectors.toMap(Series::getId, s -> s, (a, b) -> a));
        List<Long> activeSeasonIds = activeSeriesById.values().stream()
                .map(Series::getSeasonId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Season> activeSeasonById = activeSeasonIds.isEmpty()
                ? Map.of()
                : seasonService.listByIds(activeSeasonIds).stream().collect(Collectors.toMap(Season::getId, s -> s, (a, b) -> a));
        Map<Long, List<Tournament>> tournamentsBySeriesId = activeTournaments.stream()
                .filter(t -> t.getSeriesId() != null)
                .collect(Collectors.groupingBy(Tournament::getSeriesId, HashMap::new, Collectors.toList()));
        List<Map<String, Object>> activeSeriesRows = new ArrayList<>();
        for (Long sid : activeSeriesIds) {
            Series s = activeSeriesById.get(sid);
            if (s == null) continue;
            String seriesName = (s.getName() != null && !s.getName().isBlank())
                    ? s.getName().trim()
                    : ("第" + (s.getSequence() != null ? s.getSequence() : "?") + "系列");
            boolean isTestSeries = seriesName.contains("测试");
            Season se = activeSeasonById.get(s.getSeasonId());
            String seasonLabel = se == null ? "-" : (se.getYear() + "年" + (se.getHalf() == 1 ? "上半年" : "下半年"));
            List<Tournament> ts = tournamentsBySeriesId.getOrDefault(sid, List.of()).stream()
                    .sorted(Comparator.comparing(Tournament::getStartDate, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            Map<String, Object> row = new HashMap<>();
            row.put("seriesId", sid);
            row.put("seriesName", seriesName);
            row.put("seasonLabel", seasonLabel);
            row.put("isTestSeries", isTestSeries);
            row.put("entryUrl", "/season/detail/by-series/" + sid);
            row.put("tournaments", ts);
            activeSeriesRows.add(row);
        }
        activeSeriesRows.sort((a, b) -> {
            boolean at = Boolean.TRUE.equals(a.get("isTestSeries"));
            boolean bt = Boolean.TRUE.equals(b.get("isTestSeries"));
            if (at != bt) return at ? -1 : 1; // 测试系列置顶
            return String.valueOf(b.getOrDefault("seasonLabel", "")).compareTo(String.valueOf(a.getOrDefault("seasonLabel", "")));
        });
        model.addAttribute("homeActiveSeriesRows", activeSeriesRows);
        
        // 验证时区设置
        TimezoneUtil.logTimezoneInfo();
        
        return "home";
    }

    private Map<String, Object> buildTotalMedalBoard() {
        List<Tournament> tournaments = tournamentService.lambdaQuery()
                .list();
        List<String> levelCodes = tournaments == null ? List.of() : tournaments.stream()
                .map(Tournament::getLevelCode)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .distinct()
                .sorted()
                .toList();
        return buildMedalBoardByLevel(levelCodes, tournaments);
    }

    private Map<String, Object> buildSeasonMedalBoard(Season currentSeason) {
        // 赛季奖牌榜：统计当前赛季内各赛事“等级名称”为 CM1000/CM500/CM250 的赛事，
        // 以赛事等级为单位汇总金/银/铜次数（基于每届赛事最终排名第1/2/3）。
        Set<String> allowedLevelNames = Set.of("CM1000", "CM500", "CM250");
        if (currentSeason == null || currentSeason.getId() == null) {
            return Map.of("levelCodes", List.of(), "levelLabels", Map.of(), "rowsByLevel", Map.of());
        }
        List<Long> seriesIds = seriesService.lambdaQuery()
                .eq(Series::getSeasonId, currentSeason.getId())
                .list()
                .stream()
                .map(Series::getId)
                .filter(Objects::nonNull)
                .toList();
        if (seriesIds.isEmpty()) {
            return Map.of("levelCodes", List.of(), "levelLabels", Map.of(), "rowsByLevel", Map.of());
        }

        Set<String> allowedLevelCodes = tournamentLevelService.list().stream()
                .filter(Objects::nonNull)
                .filter(l -> l.getCode() != null && !l.getCode().isBlank())
                .filter(l -> {
                    String n = l.getName();
                    return n != null && allowedLevelNames.contains(n.trim());
                })
                .map(TournamentLevel::getCode)
                .collect(Collectors.toSet());
        if (allowedLevelCodes.isEmpty()) {
            return Map.of("levelCodes", List.of(), "levelLabels", Map.of(), "rowsByLevel", Map.of());
        }

        List<Tournament> tournaments = tournamentService.lambdaQuery()
                .in(Tournament::getSeriesId, seriesIds)
                .in(Tournament::getLevelCode, allowedLevelCodes)
                .eq(Tournament::getStatus, 2)
                .list();
        List<String> levelCodes = tournaments == null ? List.of() : tournaments.stream()
                .map(Tournament::getLevelCode)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .distinct()
                .sorted()
                .toList();
        return buildMedalBoardByLevel(levelCodes, tournaments);
    }

    private Map<String, Object> buildMedalBoardByLevel(List<String> levelCodes, List<Tournament> tournaments) {
        Map<String, Map<Long, int[]>> acc = new HashMap<>();
        Set<Long> allUserIds = new HashSet<>();
        int scanned = 0;
        int contributed = 0;
        for (Tournament t : tournaments == null ? List.<Tournament>of() : tournaments) {
            if (t == null || t.getId() == null || t.getLevelCode() == null || t.getLevelCode().isBlank()) continue;
            scanned++;
            List<Long> top3 = resolveTournamentTop3ByFinalRanking(t);
            if (top3.isEmpty()) continue;
            contributed++;
            Map<Long, int[]> levelMap = acc.computeIfAbsent(t.getLevelCode(), k -> new HashMap<>());
            if (top3.size() >= 1 && top3.get(0) != null) {
                levelMap.computeIfAbsent(top3.get(0), k -> new int[3])[0]++;
                allUserIds.add(top3.get(0));
            }
            if (top3.size() >= 2 && top3.get(1) != null) {
                levelMap.computeIfAbsent(top3.get(1), k -> new int[3])[1]++;
                allUserIds.add(top3.get(1));
            }
            if (top3.size() >= 3 && top3.get(2) != null) {
                levelMap.computeIfAbsent(top3.get(2), k -> new int[3])[2]++;
                allUserIds.add(top3.get(2));
            }
        }
        logger.info("MedalBoard: scanned tournaments={}, contributedTop3={}", scanned, contributed);
        Map<Long, String> usernameById = allUserIds.isEmpty() ? Map.of() : userService.listByIds(allUserIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));

        Map<String, String> levelLabels = buildLevelLabels(levelCodes);
        Map<String, List<Map<String, Object>>> rowsByLevel = new HashMap<>();
        for (String levelCode : levelCodes) {
            List<Map<String, Object>> rows = acc.getOrDefault(levelCode, Map.of()).entrySet().stream()
                    .map(e -> {
                        Long uid = e.getKey();
                        int[] c = e.getValue();
                        Map<String, Object> row = new HashMap<>();
                        row.put("userId", uid);
                        row.put("username", usernameById.getOrDefault(uid, "未知"));
                        row.put("gold", c[0]);
                        row.put("silver", c[1]);
                        row.put("bronze", c[2]);
                        row.put("total", c[0] + c[1] + c[2]);
                        return row;
                    })
                    .filter(r -> ((Integer) r.get("total")) > 0)
                    .sorted((a, b) -> {
                        int ag = (Integer) a.get("gold"), bg = (Integer) b.get("gold");
                        if (ag != bg) return Integer.compare(bg, ag);
                        int as = (Integer) a.get("silver"), bs = (Integer) b.get("silver");
                        if (as != bs) return Integer.compare(bs, as);
                        int ab = (Integer) a.get("bronze"), bb = (Integer) b.get("bronze");
                        if (ab != bb) return Integer.compare(bb, ab);
                        return String.valueOf(a.get("username")).compareTo(String.valueOf(b.get("username")));
                    })
                    .toList();
            rowsByLevel.put(levelCode, rows);
        }
        return Map.of(
                "levelCodes", levelCodes,
                "levelLabels", levelLabels,
                "rowsByLevel", rowsByLevel
        );
    }

    private Map<String, String> buildLevelLabels(List<String> levelCodes) {
        if (levelCodes == null || levelCodes.isEmpty()) {
            return Map.of();
        }
        Map<String, String> nameByCode = tournamentLevelService.list().stream()
                .filter(Objects::nonNull)
                .filter(l -> l.getCode() != null && !l.getCode().isBlank())
                .collect(Collectors.toMap(TournamentLevel::getCode, l -> {
                    String n = l.getName();
                    return (n == null || n.isBlank()) ? l.getCode() : n.trim();
                }, (a, b) -> a));
        Map<String, String> out = new HashMap<>();
        for (String c : levelCodes) {
            if (c == null) continue;
            out.put(c, nameByCode.getOrDefault(c, c));
        }
        return out;
    }

    /**
     * 取某届赛事最终排名前 3 名（冠/亚/季），用于奖牌榜统计。
     * 仅当该赛事“最终排名完整”时返回：即冠军积分满足
     *   冠军积分 = (冠军积分/人数比率) * 参赛人数
     * 其中“参赛人数”用该赛事 user_tournament_points 的有效人数近似（剔除 userId 为空）。
     */
    private List<Long> resolveTournamentTop3ByFinalRanking(Tournament tournament) {
        if (tournament == null || tournament.getId() == null) {
            return List.of();
        }
        Long tournamentId = tournament.getId();
        List<UserTournamentPoints> rows = userTournamentPointsService.lambdaQuery()
                .eq(UserTournamentPoints::getTournamentId, tournamentId)
                .orderByDesc(UserTournamentPoints::getPoints)
                .orderByAsc(UserTournamentPoints::getId)
                .list();
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<UserTournamentPoints> valid = rows.stream()
                .filter(r -> r != null && r.getUserId() != null && r.getPoints() != null)
                .toList();
        if (valid.size() < 3) {
            return List.of();
        }

        // 参赛人数 n：优先取“赛事最终排名名单”口径（报名/分组/直通/晋级等），避免仅靠 utp 行数导致 n 偏小/偏大。
        // 若名单为空（某些老数据），再回退用 utp 的有效人数近似。
        int participantCount;
        try {
            Set<Long> roster = tournamentRankingRosterService.rosterUserIdsForEventRanking(tournamentId);
            participantCount = roster == null || roster.isEmpty()
                    ? (int) valid.stream().map(UserTournamentPoints::getUserId).distinct().count()
                    : roster.size();
        } catch (RuntimeException ignored) {
            participantCount = (int) valid.stream().map(UserTournamentPoints::getUserId).distinct().count();
        }
        if (participantCount < 3) {
            return List.of();
        }

        Integer championPoints = valid.get(0).getPoints();
        if (championPoints == null) {
            return List.of();
        }

        BigDecimal ratio = tournament.getChampionPointsRatio();
        if (ratio == null) {
            TournamentLevel level = tournament.getLevelCode() == null ? null : tournamentLevelService.lambdaQuery()
                    .eq(TournamentLevel::getCode, tournament.getLevelCode())
                    .last("LIMIT 1")
                    .one();
            ratio = level == null ? null : level.getDefaultChampionRatio();
        }

        // 若没有比率信息，就无法判定“最终排名完整”，这里选择直接不统计，避免误计。
        if (ratio == null) {
            return List.of();
        }
        int expectedChampionPoints = ratio.multiply(BigDecimal.valueOf(participantCount))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
        if (!Objects.equals(expectedChampionPoints, championPoints)) {
            return List.of();
        }

        List<Long> top3 = new ArrayList<>(3);
        for (UserTournamentPoints row : valid) {
            top3.add(row.getUserId());
            if (top3.size() >= 3) break;
        }
        return top3.size() == 3 ? top3 : List.of();
    }
    
    private void addSystemStatistics(Model model) {
        // 获取系统基础统计信息
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory() / (1024 * 1024); // MB
        long freeMemory = runtime.freeMemory() / (1024 * 1024); // MB
        long usedMemory = totalMemory - freeMemory;
        double memoryUsagePercent = (double) usedMemory / totalMemory * 100;
        
        // 添加系统信息到模型
        model.addAttribute("systemInfo", Map.of(
            "javaVersion", System.getProperty("java.version"),
            "osName", System.getProperty("os.name"),
            "osVersion", System.getProperty("os.version"),
            "availableProcessors", runtime.availableProcessors(),
            "totalMemoryMB", totalMemory,
            "usedMemoryMB", usedMemory,
            "memoryUsagePercent", String.format("%.1f", memoryUsagePercent)
        ));
        
        // 添加当前时间（使用Java 8时间API）
        model.addAttribute("currentTime", java.time.LocalDateTime.now());
    }
}