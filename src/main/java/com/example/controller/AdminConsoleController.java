package com.example.controller;

import com.example.entity.Season;
import com.example.entity.Series;
import com.example.entity.TournamentCompetitionConfig;
import com.example.entity.TournamentLevel;
import com.example.service.ITournamentLevelService;
import com.example.service.RankingPriorityService;
import com.example.service.SeasonService;
import com.example.service.SeriesService;
import com.example.service.SystemConfigService;
import com.example.util.SeriesDisplayNames;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
public class AdminConsoleController {

    private final RankingPriorityService rankingPriorityService;
    private final SeriesService seriesService;
    private final SeasonService seasonService;
    private final SystemConfigService systemConfigService;
    private final ITournamentLevelService tournamentLevelService;

    public AdminConsoleController(RankingPriorityService rankingPriorityService,
                                  SeriesService seriesService,
                                  SeasonService seasonService,
                                  SystemConfigService systemConfigService,
                                  ITournamentLevelService tournamentLevelService) {
        this.rankingPriorityService = rankingPriorityService;
        this.seriesService = seriesService;
        this.seasonService = seasonService;
        this.systemConfigService = systemConfigService;
        this.tournamentLevelService = tournamentLevelService;
    }

    @GetMapping("/console")
    public String console(Model model) {
        model.addAttribute("queue", rankingPriorityService.listQueue());
        model.addAttribute("candidates", rankingPriorityService.listCandidates());
        model.addAttribute("applyToNewSeries", rankingPriorityService.applyToNewSeries());
        model.addAttribute("seriesOptions", buildSeriesOptions());
        model.addAttribute("defaultCenterLineLimit", systemConfigService.getDefaultCenterLineLimit());
        model.addAttribute("defaultDeadStoneLimit", systemConfigService.getDefaultDeadStoneLimit());
        model.addAttribute("defaultKnockoutRequirePrevStageDone",
                systemConfigService.getDefaultKnockoutRequirePrevStageDone());
        model.addAttribute("levelGroupDrawDefaults", buildLevelGroupDrawDefaults());
        return "admin-console";
    }

    @PostMapping("/console/level-group-draw/save")
    @ResponseBody
    public Map<String, Object> saveLevelGroupDrawDefaults(@RequestBody Map<String, Object> body) {
        try {
            Object raw = body == null ? null : body.get("levels");
            if (!(raw instanceof List<?> list) || list.isEmpty()) {
                return Map.of("success", false, "message", "请提交至少一个赛事等级配置");
            }
            int updated = 0;
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> row)) {
                    continue;
                }
                Integer id = toInteger(row.get("id"));
                if (id == null) {
                    continue;
                }
                TournamentLevel level = tournamentLevelService.getById(id);
                if (level == null) {
                    continue;
                }
                Boolean allowDraw = toBoolean(row.get("groupAllowDraw"));
                if (allowDraw != null) {
                    level.setGroupAllowDraw(allowDraw);
                }
                String otMode = row.get("groupOtPointsMode") == null ? null : String.valueOf(row.get("groupOtPointsMode"));
                level.setGroupOtPointsMode(TournamentCompetitionConfig.normalizeGroupOtPointsMode(otMode));
                tournamentLevelService.updateById(level);
                updated++;
            }
            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("success", true);
            ok.put("message", "已保存 " + updated + " 个赛事等级默认；仅之后新建并首次保存运行配置的赛事生效，已有赛事不受影响。");
            ok.put("updated", updated);
            return ok;
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("Unknown column") || msg.contains("group_allow_draw")
                    || msg.contains("group_ot_points_mode"))) {
                return Map.of("success", false, "message", "数据库尚未迁移小组赛平局默认列，请重建应用并执行 Flyway 后再保存");
            }
            return Map.of("success", false, "message", msg == null ? "保存失败" : msg);
        }
    }

    private List<Map<String, Object>> buildLevelGroupDrawDefaults() {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            List<TournamentLevel> levels = new ArrayList<>(tournamentLevelService.list());
            levels.sort(Comparator.comparing(TournamentLevel::getCode, Comparator.nullsLast(String::compareToIgnoreCase)));
            for (TournamentLevel level : levels) {
                if (level == null || level.getId() == null) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", level.getId());
                row.put("code", level.getCode());
                row.put("name", level.getName() == null ? level.getCode() : level.getName());
                row.put("groupAllowDraw", level.getGroupAllowDraw() == null || Boolean.TRUE.equals(level.getGroupAllowDraw()));
                row.put("groupOtPointsMode",
                        TournamentCompetitionConfig.normalizeGroupOtPointsMode(level.getGroupOtPointsMode()));
                out.add(row);
            }
        } catch (Exception e) {
            // restore 未迁列：总控台板块显示空列表，不拖垮页面
        }
        return out;
    }

    private static Integer toInteger(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        try {
            String s = String.valueOf(raw).trim();
            return s.isEmpty() ? null : Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Boolean toBoolean(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Boolean b) {
            return b;
        }
        String s = String.valueOf(raw).trim();
        if ("true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s)) {
            return true;
        }
        if ("false".equalsIgnoreCase(s) || "0".equals(s) || "no".equalsIgnoreCase(s)) {
            return false;
        }
        return null;
    }

    @PostMapping("/console/ranking-priority/save")
    @ResponseBody
    public Map<String, Object> saveRankingPriority(@RequestBody Map<String, Object> body) {
        try {
            List<String> orderedCodes = toStringList(body.get("orderedCriterionCodes"));
            List<Long> enabledSeriesIds = toLongList(body.get("enabledSeriesIds"));
            rankingPriorityService.saveQueueAndSeriesFlags(orderedCodes, enabledSeriesIds);
            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("success", true);
            ok.put("message", "排名参考优先级已保存；新建系列将默认启用，已勾选系列同步生效。踢出队列的决胜项不再参与排序，对应列也不再显示。");
            ok.put("queueSize", orderedCodes.size());
            ok.put("enabledSeriesCount", enabledSeriesIds.size());
            return ok;
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage() == null ? "保存失败" : e.getMessage());
        }
    }

    private List<Map<String, Object>> buildSeriesOptions() {
        Map<Long, Season> seasonById = new HashMap<>();
        for (Season s : seasonService.list()) {
            if (s.getId() != null) {
                seasonById.put(s.getId(), s);
            }
        }
        List<Series> all = new ArrayList<>(seriesService.list());
        all.sort(Comparator
                .comparing((Series s) -> {
                    Season se = seasonById.get(s.getSeasonId());
                    return se == null || se.getYear() == null ? 0 : se.getYear();
                }, Comparator.reverseOrder())
                .thenComparing(s -> {
                    Season se = seasonById.get(s.getSeasonId());
                    return se == null || se.getHalf() == null ? 0 : se.getHalf();
                }, Comparator.reverseOrder())
                .thenComparing(s -> s.getSequence() == null ? 0 : s.getSequence(), Comparator.reverseOrder()));

        List<Map<String, Object>> out = new ArrayList<>();
        for (Series series : all) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", series.getId());
            row.put("label", SeriesDisplayNames.seriesDisplayName(seriesService, series));
            Season season = seasonById.get(series.getSeasonId());
            String seasonLabel = season == null ? "未知赛季"
                    : season.getYear() + "年" + (Objects.equals(season.getHalf(), 1) ? "上半年" : "下半年");
            row.put("seasonLabel", seasonLabel);
            row.put("enabled", Boolean.TRUE.equals(series.getRankingPriorityEnabled()));
            out.add(row);
        }
        return out;
    }

    private static List<String> toStringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o == null) {
                continue;
            }
            String s = String.valueOf(o).trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    private static List<Long> toLongList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Long> out = new ArrayList<>();
        for (Object o : list) {
            if (o == null) {
                continue;
            }
            if (o instanceof Number n) {
                out.add(n.longValue());
            } else {
                String s = String.valueOf(o).trim();
                if (!s.isEmpty()) {
                    out.add(Long.parseLong(s));
                }
            }
        }
        return out;
    }
}
