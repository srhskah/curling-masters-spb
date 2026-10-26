package com.example.controller;

import com.example.entity.Season;
import com.example.entity.Series;
import com.example.service.RankingPriorityService;
import com.example.service.SeasonService;
import com.example.service.SeriesService;
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

    public AdminConsoleController(RankingPriorityService rankingPriorityService,
                                  SeriesService seriesService,
                                  SeasonService seasonService) {
        this.rankingPriorityService = rankingPriorityService;
        this.seriesService = seriesService;
        this.seasonService = seasonService;
    }

    @GetMapping("/console")
    public String console(Model model) {
        model.addAttribute("queue", rankingPriorityService.listQueue());
        model.addAttribute("candidates", rankingPriorityService.listCandidates());
        model.addAttribute("applyToNewSeries", rankingPriorityService.applyToNewSeries());
        model.addAttribute("seriesOptions", buildSeriesOptions());
        return "admin-console";
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
