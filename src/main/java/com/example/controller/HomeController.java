package com.example.controller;

import com.example.util.CookieUtil;
import com.example.util.TimezoneUtil;
import com.example.entity.Season;
import com.example.entity.User;
import com.example.service.RankingService;
import com.example.service.SeasonService;
import com.example.service.ITournamentRegistrationService;
import com.example.service.INotificationService;
import com.example.service.SeriesService;
import com.example.service.TournamentService;
import com.example.service.UserService;
import com.example.entity.Tournament;
import com.example.entity.Series;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.List;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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

        List<Tournament> openReg = tournamentRegistrationService.listOpenRegistrationTournaments(12, LocalDateTime.now());
        model.addAttribute("openRegistrationTournaments", openReg);

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