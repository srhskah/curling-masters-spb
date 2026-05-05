package com.example.controller;

import com.example.util.CookieUtil;
import com.example.util.TimezoneUtil;
import com.example.entity.Season;
import com.example.entity.User;
import com.example.entity.UserTournamentPoints;
import com.example.entity.TournamentLevel;
import com.example.dto.MedalTop3Resolution;
import com.example.service.RankingService;
import com.example.service.TournamentMedalStandingsService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import org.springframework.beans.factory.annotation.Autowired;

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
    @Autowired private TournamentMedalStandingsService tournamentMedalStandingsService;

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
        model.addAttribute("totalRankingEarliestSeries", resolveTotalRankingEarliestSeries());
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

    @GetMapping("/ranking/api/home/medal-contributions")
    @ResponseBody
    public Map<String, Object> medalContributions(@RequestParam String boardType,
                                                  @RequestParam String levelCode,
                                                  @RequestParam Long userId) {
        try {
            if (userId == null || levelCode == null || levelCode.isBlank()) {
                return Map.of("ok", false, "message", "参数不完整", "rows", List.of());
            }
            String bt = boardType == null ? "" : boardType.trim().toLowerCase();
            if (!"total".equals(bt) && !"season".equals(bt)) {
                return Map.of("ok", false, "message", "无效的榜单类型", "rows", List.of());
            }
            String targetLevelCode = levelCode.trim();
            List<Tournament> tournaments;
            if ("season".equals(bt)) {
                Season currentSeason = seasonService.lambdaQuery()
                        .orderByDesc(Season::getYear)
                        .orderByDesc(Season::getHalf)
                        .last("LIMIT 1")
                        .one();
                if (currentSeason == null || currentSeason.getId() == null) {
                    return Map.of("ok", true, "rows", List.of());
                }
                Set<String> allowedLevelNames = Set.of("CM1000", "CM500", "CM250");
                Set<String> allowedLevelCodes = tournamentLevelService.list().stream()
                        .filter(Objects::nonNull)
                        .filter(l -> l.getCode() != null && !l.getCode().isBlank())
                        .filter(l -> {
                            String n = l.getName();
                            return n != null && allowedLevelNames.contains(n.trim());
                        })
                        .map(TournamentLevel::getCode)
                        .collect(Collectors.toSet());
                if (!allowedLevelCodes.contains(targetLevelCode)) {
                    return Map.of("ok", true, "rows", List.of());
                }
                List<Long> seriesIds = seriesService.lambdaQuery()
                        .eq(Series::getSeasonId, currentSeason.getId())
                        .list()
                        .stream()
                        .map(Series::getId)
                        .filter(Objects::nonNull)
                        .toList();
                tournaments = seriesIds.isEmpty() ? List.of() : tournamentService.lambdaQuery()
                        .in(Tournament::getSeriesId, seriesIds)
                        .eq(Tournament::getLevelCode, targetLevelCode)
                        .list();
            } else {
                tournaments = tournamentService.lambdaQuery()
                        .eq(Tournament::getLevelCode, targetLevelCode)
                        .list();
            }

            Map<Long, Series> seriesById = tournaments == null || tournaments.isEmpty() ? Map.of() :
                    seriesService.listByIds(
                                    tournaments.stream().map(Tournament::getSeriesId).filter(Objects::nonNull).collect(Collectors.toSet()))
                            .stream().collect(Collectors.toMap(Series::getId, s -> s, (a, b) -> a));
            Map<Long, Season> seasonById = seriesById.isEmpty() ? Map.of() :
                    seasonService.listByIds(seriesById.values().stream().map(Series::getSeasonId).filter(Objects::nonNull).collect(Collectors.toSet()))
                            .stream().collect(Collectors.toMap(Season::getId, s -> s, (a, b) -> a));
            Map<String, String> levelNameByCode = tournamentLevelService.list().stream()
                    .filter(Objects::nonNull)
                    .filter(l -> l.getCode() != null && !l.getCode().isBlank())
                    .collect(Collectors.toMap(TournamentLevel::getCode, l -> {
                        String n = l.getName();
                        return n == null || n.isBlank() ? l.getCode() : n.trim();
                    }, (a, b) -> a));

            List<Map<String, Object>> rows = new ArrayList<>();
            for (Tournament t : tournaments == null ? List.<Tournament>of() : tournaments) {
                MedalTop3Resolution r = tournamentMedalStandingsService.resolveTournamentTop3ByFinalRanking(t);
                if (!r.eligible()) {
                    continue;
                }
                List<Long> top3 = r.top3();
                int idx = top3.indexOf(userId);
                if (idx < 0 || idx > 2) {
                    continue;
                }
                Series s = seriesById.get(t.getSeriesId());
                Season se = s == null ? null : seasonById.get(s.getSeasonId());
                String seasonLabel = se == null ? "-" : (se.getYear() + "年" + (Objects.equals(se.getHalf(), 1) ? "上半年" : "下半年"));
                String seriesLabel = s == null ? "-" : ((s.getName() != null && !s.getName().isBlank()) ? s.getName().trim()
                        : ("第" + (s.getSequence() == null ? "?" : s.getSequence()) + "系列"));
                String levelLabel = levelNameByCode.getOrDefault(t.getLevelCode(), t.getLevelCode());
                String rankLabel = (idx == 0) ? "第1名（🥇）" : (idx == 1) ? "第2名（🥈）" : "第3名（🥉）";
                Map<String, Object> one = new HashMap<>();
                one.put("tournamentId", t.getId());
                one.put("seasonLabel", seasonLabel);
                one.put("seriesLabel", seriesLabel);
                one.put("levelLabel", levelLabel);
                one.put("rankLabel", rankLabel);
                one.put("detailUrl", "/tournament/detail/" + t.getId());
                rows.add(one);
            }
            rows.sort((a, b) -> String.valueOf(b.getOrDefault("seasonLabel", "")).compareTo(String.valueOf(a.getOrDefault("seasonLabel", ""))));
            return Map.of("ok", true, "rows", rows);
        } catch (Exception e) {
            return Map.of("ok", false, "message", e.getMessage(), "rows", List.of());
        }
    }

    private Map<String, Object> resolveTotalRankingEarliestSeries() {
        List<UserTournamentPoints> utpRows = userTournamentPointsService.list();
        if (utpRows == null || utpRows.isEmpty()) {
            return Map.of();
        }
        Set<Long> tournamentIds = utpRows.stream()
                .map(UserTournamentPoints::getTournamentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (tournamentIds.isEmpty()) {
            return Map.of();
        }
        List<Tournament> tournaments = tournamentService.listByIds(tournamentIds);
        if (tournaments == null || tournaments.isEmpty()) {
            return Map.of();
        }
        Set<Long> seriesIds = tournaments.stream()
                .map(Tournament::getSeriesId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (seriesIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Series> seriesById = seriesService.listByIds(seriesIds).stream()
                .collect(Collectors.toMap(Series::getId, s -> s, (a, b) -> a));
        Set<Long> seasonIds = seriesById.values().stream()
                .map(Series::getSeasonId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Season> seasonById = seasonIds.isEmpty() ? Map.of() : seasonService.listByIds(seasonIds).stream()
                .collect(Collectors.toMap(Season::getId, s -> s, (a, b) -> a));

        Series earliest = seriesById.values().stream()
                .min((a, b) -> {
                    Season sa = seasonById.get(a.getSeasonId());
                    Season sb = seasonById.get(b.getSeasonId());
                    int ya = sa == null || sa.getYear() == null ? Integer.MAX_VALUE : sa.getYear();
                    int yb = sb == null || sb.getYear() == null ? Integer.MAX_VALUE : sb.getYear();
                    if (ya != yb) return Integer.compare(ya, yb);
                    int ha = sa == null || sa.getHalf() == null ? Integer.MAX_VALUE : sa.getHalf();
                    int hb = sb == null || sb.getHalf() == null ? Integer.MAX_VALUE : sb.getHalf();
                    if (ha != hb) return Integer.compare(ha, hb);
                    int qa = a.getSequence() == null ? Integer.MAX_VALUE : a.getSequence();
                    int qb = b.getSequence() == null ? Integer.MAX_VALUE : b.getSequence();
                    if (qa != qb) return Integer.compare(qa, qb);
                    Long ia = a.getId() == null ? Long.MAX_VALUE : a.getId();
                    Long ib = b.getId() == null ? Long.MAX_VALUE : b.getId();
                    return ia.compareTo(ib);
                })
                .orElse(null);
        if (earliest == null || earliest.getId() == null) {
            return Map.of();
        }
        Season season = seasonById.get(earliest.getSeasonId());
        String seasonLabel = season == null
                ? ""
                : (season.getYear() + "年" + (Objects.equals(season.getHalf(), 1) ? "上半年" : "下半年"));
        String seriesName = (earliest.getName() != null && !earliest.getName().isBlank())
                ? earliest.getName().trim()
                : ("第" + (earliest.getSequence() == null ? "?" : earliest.getSequence()) + "系列");
        Map<String, Object> out = new HashMap<>();
        out.put("seriesId", earliest.getId());
        out.put("seriesName", seriesName);
        out.put("seasonLabel", seasonLabel);
        out.put("entryUrl", "/season/detail/by-series/" + earliest.getId());
        return out;
    }

    private Map<String, Object> buildTotalMedalBoard() {
        List<Tournament> tournaments = tournamentService.lambdaQuery().list();
        List<String> levelCodes = (tournaments == null ? List.<Tournament>of() : tournaments).stream()
                .map(Tournament::getLevelCode)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .sorted()
                .toList();
        return buildMedalBoard(levelCodes, tournaments);
    }

    private Map<String, Object> buildSeasonMedalBoard(Season currentSeason) {
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

        List<String> levelCodes = tournamentLevelService.list().stream()
                .filter(Objects::nonNull)
                .filter(l -> l.getCode() != null && !l.getCode().isBlank())
                .filter(l -> {
                    String n = l.getName();
                    return n != null && allowedLevelNames.contains(n.trim());
                })
                .map(TournamentLevel::getCode)
                .distinct()
                .sorted()
                .toList();
        List<Tournament> tournaments = tournamentService.lambdaQuery()
                .in(Tournament::getSeriesId, seriesIds)
                .in(Tournament::getLevelCode, allowedLevelCodes)
                .list();
        return buildMedalBoard(levelCodes, tournaments);
    }

    private Map<String, Object> buildMedalBoard(List<String> levelCodes, List<Tournament> tournaments) {
        Map<String, Map<Long, int[]>> acc = new HashMap<>();
        Set<Long> allUserIds = new HashSet<>();
        Map<String, Integer> skippedReasons = new HashMap<>();
        int scanned = 0;
        int contributed = 0;

        for (Tournament tournament : tournaments == null ? List.<Tournament>of() : tournaments) {
            if (tournament == null || tournament.getId() == null) {
                incrementReason(skippedReasons, "invalid_tournament");
                continue;
            }
            String levelCode = tournament.getLevelCode() == null ? "" : tournament.getLevelCode().trim();
            if (levelCode.isBlank()) {
                incrementReason(skippedReasons, "blank_level_code");
                continue;
            }
            scanned++;
            MedalTop3Resolution result = tournamentMedalStandingsService.resolveTournamentTop3ByFinalRanking(tournament);
            if (!result.eligible()) {
                incrementReason(skippedReasons, result.reason());
                continue;
            }
            List<Long> top3 = result.top3();
            if (top3.size() < 3) {
                incrementReason(skippedReasons, "top3_incomplete");
                continue;
            }
            contributed++;
            Map<Long, int[]> levelMap = acc.computeIfAbsent(levelCode, k -> new HashMap<>());
            for (int medalIdx = 0; medalIdx < 3; medalIdx++) {
                Long userId = top3.get(medalIdx);
                if (userId == null) {
                    continue;
                }
                levelMap.computeIfAbsent(userId, k -> new int[3])[medalIdx]++;
                allUserIds.add(userId);
            }
        }

        logger.info("MedalBoard rebuilt: scanned={}, contributed={}, skipped={}", scanned, contributed, skippedReasons);
        Map<Long, String> usernameById = allUserIds.isEmpty() ? Map.of() : userService.listByIds(allUserIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));

        Map<String, String> levelLabels = buildLevelLabels(levelCodes);
        Map<String, String> levelTabIds = buildLevelTabIds(levelCodes);
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
        List<String> orderedLevelCodes = levelCodes == null ? List.of() : levelCodes.stream()
                .sorted((a, b) -> {
                    boolean aHasRows = !rowsByLevel.getOrDefault(a, List.of()).isEmpty();
                    boolean bHasRows = !rowsByLevel.getOrDefault(b, List.of()).isEmpty();
                    if (aHasRows != bHasRows) {
                        return aHasRows ? -1 : 1;
                    }
                    String al = levelLabels.getOrDefault(a, a);
                    String bl = levelLabels.getOrDefault(b, b);
                    return al.compareTo(bl);
                })
                .toList();
        List<Map<String, Object>> tabs = orderedLevelCodes.stream().map(code -> {
            Map<String, Object> tab = new HashMap<>();
            tab.put("code", code);
            tab.put("label", levelLabels.getOrDefault(code, code));
            tab.put("tabId", levelTabIds.getOrDefault(code, code));
            tab.put("rows", rowsByLevel.getOrDefault(code, List.of()));
            return tab;
        }).toList();
        return Map.of(
                "tabs", tabs
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

    private Map<String, String> buildLevelTabIds(List<String> levelCodes) {
        if (levelCodes == null || levelCodes.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new HashMap<>();
        for (int i = 0; i < levelCodes.size(); i++) {
            String code = levelCodes.get(i);
            if (code == null) {
                continue;
            }
            String normalized = code.trim().toLowerCase()
                    .replaceAll("[^a-z0-9_-]+", "-")
                    .replaceAll("^-+", "")
                    .replaceAll("-+$", "");
            if (normalized.isBlank()) {
                normalized = "level";
            }
            out.put(code, normalized + "-" + i);
        }
        return out;
    }

    private void incrementReason(Map<String, Integer> skippedReasons, String reason) {
        String key = (reason == null || reason.isBlank()) ? "unknown" : reason;
        skippedReasons.merge(key, 1, Integer::sum);
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