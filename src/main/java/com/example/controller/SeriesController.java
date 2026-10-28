package com.example.controller;

import com.example.entity.*;
import com.example.service.*;
import com.example.util.IpAddressUtil;
import com.example.util.HtmlEscaper;
import com.example.util.SeriesDisplayNames;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 赛事系列控制器
 * 管理赛季内的系列赛事
 * 
 * @author Curling Masters
 * @since 2026-03-04
 */
@Controller
@RequestMapping("/series")
public class SeriesController {

    @Autowired
    private SeriesService seriesService;
    
    @Autowired
    private SeasonService seasonService;
    
    @Autowired
    private TournamentService tournamentService;
    
    @Autowired
    private UserService userService;

    @Autowired
    private RankingPriorityService rankingPriorityService;

    @Autowired
    private ITournamentLevelService tournamentLevelService;

    @Autowired
    private TournamentUrlService tournamentUrlService;
    
    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            String username = auth.getName();
            User user = userService.findByUsername(username);
            return user != null && user.getRole() <= 1;
        }
        return false;
    }
    
    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return userService.findByUsername(auth.getName());
        }
        return null;
    }

    private String buildSeriesDisplayName(Series series) {
        return SeriesDisplayNames.seriesDisplayName(seriesService, series);
    }

    @GetMapping("/list")
    public String seriesList(@RequestParam(required = false) Long seasonId, Model model, HttpServletRequest request) {
        String clientIp = IpAddressUtil.getClientIpAddress(request);
        String ipType = IpAddressUtil.classifyIpAddress(clientIp);
        model.addAttribute("clientIp", clientIp);
        model.addAttribute("ipType", ipType);
        
        List<Season> seasons = seasonService.list();
        model.addAttribute("seasons", seasons);
        
        List<Series> seriesList;
        if (seasonId != null) {
            seriesList = seriesService.lambdaQuery().eq(Series::getSeasonId, seasonId).orderByAsc(Series::getSequence).list();
        } else {
            seriesList = seriesService.list();
        }

        Map<String, String> levelNameByCode = new LinkedHashMap<>();
        List<TournamentLevel> levels = tournamentLevelService != null ? tournamentLevelService.list() : List.of();
        if (levels != null) {
            for (TournamentLevel lv : levels) {
                if (lv == null || lv.getCode() == null || lv.getCode().isBlank()) {
                    continue;
                }
                String label = lv.getName() != null && !lv.getName().isBlank() ? lv.getName() : lv.getCode();
                levelNameByCode.putIfAbsent(lv.getCode(), label);
            }
        }
        Map<Long, LinkedHashSet<String>> levelsBySeriesId = new LinkedHashMap<>();
        Map<Long, Long> tournamentCountBySeriesId = new HashMap<>();
        List<Tournament> allTournaments = tournamentService.list();
        if (allTournaments != null) {
            for (Tournament t : allTournaments) {
                if (t == null || t.getSeriesId() == null) {
                    continue;
                }
                tournamentCountBySeriesId.merge(t.getSeriesId(), 1L, Long::sum);
                if (t.getLevelCode() == null || t.getLevelCode().isBlank()) {
                    continue;
                }
                String label = levelNameByCode.getOrDefault(t.getLevelCode(), t.getLevelCode());
                levelsBySeriesId.computeIfAbsent(t.getSeriesId(), k -> new LinkedHashSet<>()).add(label);
            }
        }
        
        // 使用Map来存储额外信息
        List<Map<String, Object>> seriesInfoList = new ArrayList<>();
        for (Series s : seriesList) {
            Map<String, Object> seriesInfo = new HashMap<>();
            seriesInfo.put("series", s);
            
            Season season = seasonService.getById(s.getSeasonId());
            seriesInfo.put("seasonName", season != null ? season.getYear() + "年" + (season.getHalf() == 1 ? "上半年" : "下半年") : "未知");
            long tournamentCount = tournamentCountBySeriesId.getOrDefault(s.getId(), 0L);
            seriesInfo.put("tournamentCount", tournamentCount);
            LinkedHashSet<String> seriesLevels = s.getId() != null
                    ? levelsBySeriesId.getOrDefault(s.getId(), new LinkedHashSet<>())
                    : new LinkedHashSet<>();
            seriesInfo.put("levelLabels", new ArrayList<>(seriesLevels));
            
            seriesInfoList.add(seriesInfo);
        }
        
        // 构建通用列表数据
        List<Map<String, Object>> dataList = new ArrayList<>();
        for (Map<String, Object> seriesInfo : seriesInfoList) {
            Series series = (Series) seriesInfo.get("series");
            String seasonName = (String) seriesInfo.get("seasonName");
            long tournamentCount = ((Number) seriesInfo.get("tournamentCount")).longValue();
            
            Map<String, Object> item = new HashMap<>();
            
            // 构建创建时间显示
            String createTimeStr = series.getCreateTime() != null ? 
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(series.getCreateTime()) : "-";
            
            // 构建系列名称
            String seriesName = buildSeriesDisplayName(series);
            @SuppressWarnings("unchecked")
            List<String> levelLabels = (List<String>) seriesInfo.get("levelLabels");
            if (levelLabels == null) {
                levelLabels = List.of();
            }
            StringBuilder levelHtml = new StringBuilder();
            if (levelLabels.isEmpty()) {
                levelHtml.append("-");
            } else {
                for (String lab : levelLabels) {
                    if (lab == null || lab.isBlank()) {
                        continue;
                    }
                    levelHtml.append("<span class=\"badge bg-light text-dark border me-1 mb-1\">")
                            .append(HtmlEscaper.escapeHtml(lab))
                            .append("</span>");
                }
            }
            String levelPlain = String.join("、", levelLabels);
            
            item.put("data", Arrays.asList(
                series.getId(),
                "<strong>" + HtmlEscaper.escapeHtml(seriesName) + "</strong>",
                seasonName,
                series.getSequence(),
                "-", // Series表没有status字段，显示占位符
                "<span class=\"badge bg-info\">" + tournamentCount + " 个</span>",
                levelHtml.toString(),
                createTimeStr
            ));
            item.put("filters", (seasonName != null ? seasonName : "") + " " + (seriesName != null ? seriesName : "") + " " + levelPlain);
            item.put("id", series.getId());
            item.put("seasonDetailUrl", tournamentUrlService.seasonDetailBySeriesUrl(series.getId()));
            
            dataList.add(item);
        }
        
        // 构建列配置
        List<Map<String, Object>> columns = new ArrayList<>();
        columns.add(Map.of("title", "ID", "type", "text"));
        columns.add(Map.of("title", "系列名称", "type", "custom"));
        columns.add(Map.of("title", "所属赛季", "type", "text"));
        columns.add(Map.of("title", "序列号", "type", "text"));
        columns.add(Map.of("title", "状态", "type", "custom"));
        columns.add(Map.of("title", "赛事数量", "type", "custom"));
        columns.add(Map.of("title", "该系列下的赛事等级", "type", "custom"));
        columns.add(Map.of("title", "创建时间", "type", "text"));
        
        // 构建操作按钮
        List<Map<String, Object>> actions = new ArrayList<>();
        actions.add(Map.of("urlField", "seasonDetailUrl", "urlPrefix", "/season/detail/by-series/", "btnClass", "btn btn-sm btn-outline-info", "icon", "bi bi-eye", "text", "详情"));
        actions.add(Map.of("urlPrefix", "/series/edit/", "btnClass", "btn btn-sm btn-outline-warning", "icon", "bi bi-pencil", "text", "编辑"));
        actions.add(Map.of("urlPrefix", "/series/delete/", "method", "post", "btnClass", "btn btn-sm btn-outline-danger", "icon", "bi bi-trash", "text", "删除", "confirm", "确定要删除这个赛事系列吗？"));
        
        User currentUser = getCurrentUser();
        boolean admin = isAdmin();
        
        // 通用列表参数
        model.addAttribute("pageTitle", "赛事系列列表");
        model.addAttribute("pageIcon", "bi bi-collection");
        model.addAttribute("entityName", "系列");
        model.addAttribute("addUrl", "/series/add");
        model.addAttribute("dataList", dataList);
        model.addAttribute("columns", columns);
        model.addAttribute("actions", actions);
        model.addAttribute("hasActions", true);
        model.addAttribute("emptyIcon", "bi bi-collection");
        model.addAttribute("emptyMessage", "暂无赛事系列数据");
        model.addAttribute("isAdmin", admin);
        model.addAttribute("currentUser", currentUser);
        
        return "generic-list";
    }
    
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @GetMapping("/add")
    public String addSeriesPage(@RequestParam(required = false) Long seasonId, Model model, HttpServletRequest request) {
        String clientIp = IpAddressUtil.getClientIpAddress(request);
        String ipType = IpAddressUtil.classifyIpAddress(clientIp);
        
        List<Season> seasons = seasonService.lambdaQuery()
                .orderByDesc(Season::getYear)
                .orderByDesc(Season::getHalf)
                .list();
        
        // 构建赛季选项
        List<Map<String, Object>> seasonOptions = new ArrayList<>();
        for (Season season : seasons) {
            seasonOptions.add(Map.of(
                "value", season.getId(),
                "text", season.getYear() + "年" + (season.getHalf() == 1 ? "上半年" : "下半年"),
                "selected", season.getId().equals(seasonId)
            ));
        }
        
        // 构建表单字段配置
        List<Map<String, Object>> fields = new ArrayList<>();
        
        fields.add(Map.of(
            "type", "select",
            "id", "seasonId",
            "name", "seasonId",
            "label", "所属赛季",
            "placeholder", "请选择赛季",
            "required", true,
            "help", "选择该赛事系列所属的赛季",
            "options", seasonOptions
        ));
        
        fields.add(Map.of(
            "type", "text",
            "id", "name",
            "name", "name",
            "label", "系列名称",
            "placeholder", "可选的自定义名称，如'春季系列'等",
            "help", "可选的自定义名称，不填则显示为'第X系列'"
        ));
        
        // 通用表单参数
        model.addAttribute("pageTitle", "添加赛事系列");
        model.addAttribute("pageIcon", "bi bi-collection");
        model.addAttribute("saveUrl", seasonId != null ? "/series/save?returnSeasonId=" + seasonId : "/series/save");
        model.addAttribute("backUrl", seasonId != null ? tournamentUrlService.seasonDetailUrl(seasonId) : "/series/list");
        Series formData = new Series();
        formData.setSeasonId(seasonId);
        model.addAttribute("formData", formData);
        model.addAttribute("fields", fields);
        model.addAttribute("clientIp", clientIp);
        model.addAttribute("ipType", ipType);
        
        return "generic-form";
    }
    
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @GetMapping("/edit/{id}")
    public String editSeriesPage(@PathVariable Long id, Model model, HttpServletRequest request) {
        String clientIp = IpAddressUtil.getClientIpAddress(request);
        String ipType = IpAddressUtil.classifyIpAddress(clientIp);
        
        Series series = seriesService.getById(id);
        if (series == null) {
            return "redirect:/series/list";
        }
        
        List<Season> seasons = seasonService.lambdaQuery()
                .orderByDesc(Season::getYear)
                .orderByDesc(Season::getHalf)
                .list();
        
        // 构建赛季选项
        List<Map<String, Object>> seasonOptions = new ArrayList<>();
        for (Season season : seasons) {
            seasonOptions.add(Map.of(
                "value", season.getId(),
                "text", season.getYear() + "年" + (season.getHalf() == 1 ? "上半年" : "下半年"),
                "selected", season.getId().equals(series.getSeasonId())
            ));
        }
        
        // 构建表单字段配置
        List<Map<String, Object>> fields = new ArrayList<>();
        
        fields.add(Map.of(
            "type", "select",
            "id", "seasonId",
            "name", "seasonId",
            "label", "所属赛季",
            "placeholder", "请选择赛季",
            "required", true,
            "help", "选择该赛事系列所属的赛季",
            "options", seasonOptions
        ));
        
        fields.add(Map.of(
            "type", "number",
            "id", "sequence",
            "name", "sequence",
            "label", "序列号",
            "placeholder", "如：1、2、3等",
            "min", "1",
            "required", false,
            "help", "可选：留空则保留原序列号（编辑）/自动自增（新增）"
        ));
        
        fields.add(Map.of(
            "type", "text",
            "id", "name",
            "name", "name",
            "label", "系列名称",
            "placeholder", "可选的自定义名称，如'春季系列'等",
            "help", "可选的自定义名称，不填则显示为'第X系列'"
        ));
        
        // 通用表单参数
        model.addAttribute("pageTitle", "编辑赛事系列");
        model.addAttribute("pageIcon", "bi bi-collection");
        model.addAttribute("saveUrl", "/series/save");
        model.addAttribute("backUrl", "/series/list");
        model.addAttribute("formData", series);
        model.addAttribute("fields", fields);
        model.addAttribute("clientIp", clientIp);
        model.addAttribute("ipType", ipType);
        
        return "generic-form";
    }
    
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @PostMapping("/save")
    public String saveSeries(
            @ModelAttribute Series series,
            @RequestParam(required = false) Long returnSeasonId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            if (series.getSeasonId() == null) {
                redirectAttributes.addFlashAttribute("error", "请选择赛季");
                return series.getSeasonId() != null ? "redirect:/series/add?seasonId=" + series.getSeasonId() : "redirect:/series/add";
            }

            // 序列号：
            // - 新增：赛季内自增（允许手动填；为空则自动取 max+1）
            // - 编辑：允许清空；清空则保留原序列号
            if (series.getSequence() == null) {
                if (series.getId() != null) {
                    Series old = seriesService.getById(series.getId());
                    if (old != null && old.getSequence() != null) {
                        series.setSequence(old.getSequence());
                    }
                }
            }
            if (series.getSequence() == null) {
                Integer maxSeq = seriesService.lambdaQuery()
                        .eq(Series::getSeasonId, series.getSeasonId())
                        .select(Series::getSequence)
                        .list()
                        .stream()
                        .map(Series::getSequence)
                        .filter(Objects::nonNull)
                        .max(Integer::compareTo)
                        .orElse(0);
                series.setSequence(maxSeq + 1);
            }
            
            boolean exists = seriesService.lambdaQuery()
                .eq(Series::getSeasonId, series.getSeasonId())
                .eq(Series::getSequence, series.getSequence())
                .ne(series.getId() != null, Series::getId, series.getId())
                .exists();
            
            if (exists) {
                redirectAttributes.addFlashAttribute("error", "该赛季下已存在相同序号的系列赛事");
                return series.getId() == null
                        ? (series.getSeasonId() != null ? "redirect:/series/add?seasonId=" + series.getSeasonId() : "redirect:/series/add")
                        : "redirect:/series/edit/" + series.getId();
            }
            
            if (series.getId() == null) {
                series.setCreatedAt(LocalDateTime.now());
                if (rankingPriorityService.applyToNewSeries()) {
                    series.setRankingPriorityEnabled(true);
                } else if (series.getRankingPriorityEnabled() == null) {
                    series.setRankingPriorityEnabled(false);
                }
                seriesService.save(series);
                redirectAttributes.addFlashAttribute("success", "系列赛事创建成功");
            } else {
                Series old = seriesService.getById(series.getId());
                if (old != null && series.getRankingPriorityEnabled() == null) {
                    series.setRankingPriorityEnabled(old.getRankingPriorityEnabled());
                }
                seriesService.updateById(series);
                redirectAttributes.addFlashAttribute("success", "系列赛事更新成功");
            }
            
            return returnSeasonId != null ? "redirect:" + tournamentUrlService.seasonDetailUrl(returnSeasonId) : "redirect:/series/list";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "操作失败：" + e.getMessage());
            return series.getId() == null ? "redirect:/series/add" : "redirect:/series/edit/" + series.getId();
        }
    }
    
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @PostMapping("/delete/{id}")
    public String deleteSeries(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            long tournamentCount = tournamentService.lambdaQuery()
                .eq(com.example.entity.Tournament::getSeriesId, id)
                .count();
            
            if (tournamentCount > 0) {
                redirectAttributes.addFlashAttribute("error", "该系列赛事下有 " + tournamentCount + " 个赛事，无法删除");
                return "redirect:/series/list";
            }
            
            seriesService.removeById(id);
            redirectAttributes.addFlashAttribute("success", "系列赛事删除成功");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "删除失败：" + e.getMessage());
        }
        return "redirect:/series/list";
    }
}