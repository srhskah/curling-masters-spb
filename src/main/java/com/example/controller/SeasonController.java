package com.example.controller;

import com.example.entity.Season;
import com.example.entity.TournamentLevel;
import com.example.entity.User;
import com.example.dto.RankingEntry;
import com.example.service.SeasonService;
import com.example.service.UserService;
import com.example.service.ITournamentLevelService;
import com.example.service.SeriesService;
import com.example.service.TournamentService;
import com.example.service.ITournamentRegistrationService;
import com.example.service.RankingService;
import com.example.entity.Series;
import com.example.entity.Tournament;
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

/**
 * 赛季管理控制器
 * 支持赛季的全生命周期管理，包括创建、编辑、删除和查看
 * 
 * @author Curling Masters
 * @since 2026-03-04
 */
@Controller
@RequestMapping("/season")
public class SeasonController {

    @Autowired
    private SeasonService seasonService;
    
    @Autowired
    private UserService userService;

    @Autowired
    private RankingService rankingService;
    
    @Autowired
    private ITournamentLevelService tournamentLevelService;
    
    @Autowired
    private SeriesService seriesService;
    
    @Autowired
    private TournamentService tournamentService;

    @Autowired
    private ITournamentRegistrationService tournamentRegistrationService;

    /**
     * 赛季与赛事等级管理页面（统一管理入口）
     */
    @GetMapping("/management")
    public String seasonManagement(Model model, HttpServletRequest request) {
        // 获取赛季列表
        List<Season> seasons = seasonService.list();
        model.addAttribute("seasons", seasons);
        
        // 获取赛事等级列表
        List<TournamentLevel> levels = tournamentLevelService.list();
        model.addAttribute("levels", levels);
        
        // 获取当前用户信息以判断权限
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = false;
        if (auth != null && auth.isAuthenticated()) {
            String username = auth.getName();
            User user = userService.findByUsername(username);
            if (user != null && user.getRole() <= 1) { // 管理员
                isAdmin = true;
            }
        }
        model.addAttribute("isAdmin", isAdmin);
        
        return "season-management";
    }
    
    /**
     * 赛季详情页面（按赛事系列呈现该赛季历届赛事）
     */
    @GetMapping("/detail/{id}")
    public String seasonDetail(@PathVariable Long id,
                               @RequestParam(required = false) Long seriesId,
                               Model model,
                               HttpServletRequest request) {
        // 获取赛季信息
        Season season = seasonService.getById(id);
        if (season == null) {
            return "redirect:/season/management";
        }
        model.addAttribute("season", season);
        
        // 获取该赛季的所有系列赛事
        List<Series> seriesList = seriesService.lambdaQuery()
                .eq(Series::getSeasonId, id)
                .orderByAsc(Series::getSequence)
                .list();

        // 跨赛季：上赛季最后一个系列 / 下赛季第一个系列（若存在）
        Season prevSeason = null;
        Season nextSeason = null;
        List<Season> allSeasons = seasonService.lambdaQuery()
                .orderByAsc(Season::getYear)
                .orderByAsc(Season::getHalf)
                .list();
        int seasonIdx = -1;
        for (int i = 0; i < allSeasons.size(); i++) {
            if (allSeasons.get(i) != null && Objects.equals(allSeasons.get(i).getId(), id)) {
                seasonIdx = i;
                break;
            }
        }
        if (seasonIdx > 0) prevSeason = allSeasons.get(seasonIdx - 1);
        if (seasonIdx >= 0 && seasonIdx < allSeasons.size() - 1) nextSeason = allSeasons.get(seasonIdx + 1);

        Long prevSeasonLastSeriesId = null;
        String prevSeasonLastSeriesLabel = null;
        if (prevSeason != null && prevSeason.getId() != null) {
            List<Series> prevSeries = seriesService.lambdaQuery()
                    .eq(Series::getSeasonId, prevSeason.getId())
                    .orderByAsc(Series::getSequence)
                    .list();
            Series last = prevSeries.stream()
                    .max(Comparator.comparing(Series::getSequence, Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElse(null);
            if (last != null && last.getId() != null) {
                prevSeasonLastSeriesId = last.getId();
                // 复用本控制器内部的展示名算法（用 seasonId=prevSeason.id）
                long namedCount = prevSeries.stream()
                        .filter(s -> s.getSequence() != null && last.getSequence() != null && s.getSequence() <= last.getSequence())
                        .filter(s -> s.getName() != null && !s.getName().trim().isEmpty())
                        .count();
                int displayIdx = last.getSequence() != null ? (int) (last.getSequence() - namedCount) : 0;
                if (displayIdx < 1) displayIdx = 1;
                String seriesDisplayName = (last.getName() != null && !last.getName().trim().isEmpty())
                        ? last.getName().trim()
                        : ("第" + (last.getSequence() != null ? displayIdx : "?") + "系列");
                String seasonLabel = prevSeason.getYear() + "年" + (prevSeason.getHalf() == 1 ? "上半年" : "下半年");
                prevSeasonLastSeriesLabel = seasonLabel + " - " + seriesDisplayName;
            }
        }

        Long nextSeasonFirstSeriesId = null;
        String nextSeasonFirstSeriesLabel = null;
        if (nextSeason != null && nextSeason.getId() != null) {
            List<Series> nextSeries = seriesService.lambdaQuery()
                    .eq(Series::getSeasonId, nextSeason.getId())
                    .orderByAsc(Series::getSequence)
                    .list();
            Series first = nextSeries.stream()
                    .min(Comparator.comparing(Series::getSequence, Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElse(null);
            if (first != null && first.getId() != null) {
                nextSeasonFirstSeriesId = first.getId();
                long namedCount = nextSeries.stream()
                        .filter(s -> s.getSequence() != null && first.getSequence() != null && s.getSequence() <= first.getSequence())
                        .filter(s -> s.getName() != null && !s.getName().trim().isEmpty())
                        .count();
                int displayIdx = first.getSequence() != null ? (int) (first.getSequence() - namedCount) : 0;
                if (displayIdx < 1) displayIdx = 1;
                String seriesDisplayName = (first.getName() != null && !first.getName().trim().isEmpty())
                        ? first.getName().trim()
                        : ("第" + (first.getSequence() != null ? displayIdx : "?") + "系列");
                String seasonLabel = nextSeason.getYear() + "年" + (nextSeason.getHalf() == 1 ? "上半年" : "下半年");
                nextSeasonFirstSeriesLabel = seasonLabel + " - " + seriesDisplayName;
            }
        }

        model.addAttribute("prevSeasonLastSeriesId", prevSeasonLastSeriesId);
        model.addAttribute("prevSeasonLastSeriesLabel", prevSeasonLastSeriesLabel);
        model.addAttribute("nextSeasonFirstSeriesId", nextSeasonFirstSeriesId);
        model.addAttribute("nextSeasonFirstSeriesLabel", nextSeasonFirstSeriesLabel);

        // 默认展示：本赛季最新系列（sequence 最大）；若传入 seriesId，则优先展示该系列
        Long latestSeriesId = seriesList.stream()
                .max(Comparator.comparing(Series::getSequence, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(Series::getId)
                .orElse(null);
        if (seriesId != null && seriesList.stream().anyMatch(s -> seriesId.equals(s.getId()))) {
            latestSeriesId = seriesId;
        }
        model.addAttribute("latestSeriesId", latestSeriesId);
        
        // 为每个系列赛事获取对应的赛事信息
        List<Map<String, Object>> seriesInfoList = new ArrayList<>();

        // 计算本赛季“届次”：同赛季 + 同赛事等级内，第 N 届（按 createdAt 升序）
        List<Long> seriesIdsInSeason = seriesList.stream()
                .filter(s -> !isTestSeries(s))
                .map(Series::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, Integer> editionByTournamentId = new HashMap<>();
        if (!seriesIdsInSeason.isEmpty()) {
            List<Tournament> allTournamentsInSeason = tournamentService.lambdaQuery()
                    .in(Tournament::getSeriesId, seriesIdsInSeason)
                    .list();
            Map<String, List<Tournament>> byLevel = new HashMap<>();
            for (Tournament t : allTournamentsInSeason) {
                if (t.getLevelCode() == null) continue;
                byLevel.computeIfAbsent(t.getLevelCode(), k -> new ArrayList<>()).add(t);
            }
            for (List<Tournament> group : byLevel.values()) {
                group.sort(Comparator.comparing(Tournament::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Tournament::getId, Comparator.nullsLast(Comparator.naturalOrder())));
                for (int i = 0; i < group.size(); i++) {
                    Tournament t = group.get(i);
                    if (t.getId() != null) editionByTournamentId.put(t.getId(), i + 1);
                }
            }
        }

        for (Series series : seriesList) {
            // 系列展示名称（遵循“序列号 - 已命名数”规则）
            long namedCount = seriesService.lambdaQuery()
                    .eq(Series::getSeasonId, id)
                    .le(Series::getSequence, series.getSequence())
                    .list()
                    .stream()
                    .filter(s -> s.getName() != null && !s.getName().trim().isEmpty())
                    .count();
            int displayIdx = series.getSequence() != null ? (int) (series.getSequence() - namedCount) : 0;
            if (displayIdx < 1) displayIdx = 1;
            String seriesDisplayName = (series.getName() != null && !series.getName().trim().isEmpty())
                    ? series.getName().trim()
                    : ("第" + (series.getSequence() != null ? displayIdx : "?") + "系列");

            List<Tournament> tournaments = tournamentService.lambdaQuery()
                    .eq(Tournament::getSeriesId, series.getId())
                    .list();
            
            // 为每个赛事添加额外信息
            List<Map<String, Object>> tournamentInfoList = new ArrayList<>();
            // 当前系列包含的赛事等级（用于概览展示）
            Map<String, String> levelCodeToName = new LinkedHashMap<>();
            for (Tournament tournament : tournaments) {
                Map<String, Object> tournamentInfo = new HashMap<>();
                tournamentInfo.put("tournament", tournament);
                
                // 获取等级名称
                TournamentLevel level = tournamentLevelService.lambdaQuery()
                        .eq(TournamentLevel::getCode, tournament.getLevelCode())
                        .one();
                String levelName = level != null ? level.getName() : tournament.getLevelCode();
                tournamentInfo.put("levelName", levelName);
                if (tournament.getLevelCode() != null) {
                    levelCodeToName.putIfAbsent(tournament.getLevelCode(), levelName);
                }

                // 届次（用于前端显示“第X届”）
                tournamentInfo.put("edition", tournament.getId() != null ? editionByTournamentId.get(tournament.getId()) : null);
                
                // 获取主办用户名称
                User hostUser = userService.getById(tournament.getHostUserId());
                tournamentInfo.put("hostUserName", hostUser != null ? hostUser.getUsername() : "未知");
                LocalDateTime nowReg = LocalDateTime.now();
                tournamentInfo.put("registrationOpen", tournamentRegistrationService.isRegistrationOpen(tournament, nowReg));
                tournamentInfo.put("registrationModuleActive", tournamentRegistrationService.registrationModuleActive(tournament, nowReg));
                tournamentInfo.put("registrationEnabled", tournamentRegistrationService.isRegistrationEnabled(tournament));
                
                tournamentInfoList.add(tournamentInfo);
            }
            
            Map<String, Object> seriesInfo = new HashMap<>();
            seriesInfo.put("series", series);
            seriesInfo.put("seriesDisplayName", seriesDisplayName);
            seriesInfo.put("tournaments", tournamentInfoList);
            seriesInfo.put("tournamentCount", tournaments.size());
            seriesInfo.put("levelCodeToName", levelCodeToName);
            
            seriesInfoList.add(seriesInfo);
        }
        
        model.addAttribute("seriesInfoList", seriesInfoList);
        
        // 获取当前用户信息以判断权限
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = false;
        if (auth != null && auth.isAuthenticated()) {
            String username = auth.getName();
            User user = userService.findByUsername(username);
            if (user != null && user.getRole() <= 1) { // 管理员
                isAdmin = true;
            }
        }
        model.addAttribute("isAdmin", isAdmin);

        // 当前赛季完整排名
        List<RankingEntry> seasonRanking = rankingService.getSeasonRanking(id, null);
        model.addAttribute("seasonRanking", seasonRanking);
        
        return "season-detail";
    }

    private static boolean isTestSeries(Series series) {
        if (series == null) return false;
        String name = series.getName();
        return name != null && name.contains("测试");
    }

    /**
     * 从“赛事系列管理”跳转到所属赛季详情并选中该系列
     */
    @GetMapping("/detail/by-series/{seriesId}")
    public String redirectSeasonDetailBySeries(@PathVariable Long seriesId, RedirectAttributes redirectAttributes) {
        Series series = seriesService.getById(seriesId);
        if (series == null || series.getSeasonId() == null) {
            redirectAttributes.addFlashAttribute("error", "系列不存在或未绑定赛季");
            return "redirect:/series/list";
        }
        return "redirect:/season/detail/" + series.getSeasonId() + "?seriesId=" + seriesId;
    }
    
    /**
     * 赛季列表页面（所有用户可查看）
     */
    @GetMapping("/list")
    public String seasonList(Model model, HttpServletRequest request) {
        // 获取所有赛季
        List<Season> seasonList = seasonService.lambdaQuery()
                .orderByDesc(Season::getYear)
                .orderByDesc(Season::getHalf)
                .list();
        
        // 构建通用列表数据
        List<Map<String, Object>> dataList = new ArrayList<>();
        for (Season season : seasonList) {
            Map<String, Object> item = new HashMap<>();
            Map<String, Object> filters = new HashMap<>();
            
            // 构建创建时间显示
            String createTimeStr = season.getCreateTime() != null ? 
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(season.getCreateTime()) : "-";
            
            item.put("data", Arrays.asList(
                season.getId(),
                season.getYear(),
                season.getHalf() == 1 ? "上半年" : "下半年",
                "-", // Season表没有status字段，显示占位符
                createTimeStr,
                "-" // Season表没有description字段，显示占位符
            ));
            filters.put("status", 0); // 默认状态
            item.put("filters", filters);
            item.put("id", season.getId());
            
            dataList.add(item);
        }
        
        // 构建列配置
        List<Map<String, Object>> columns = new ArrayList<>();
        columns.add(Map.of("title", "ID", "type", "text"));
        columns.add(Map.of("title", "年份", "type", "text"));
        columns.add(Map.of("title", "半年", "type", "text"));
        columns.add(Map.of("title", "状态", "type", "custom"));
        columns.add(Map.of("title", "创建时间", "type", "text"));
        columns.add(Map.of("title", "描述", "type", "text"));
        
        // 构建操作按钮
        List<Map<String, Object>> actions = new ArrayList<>();
        actions.add(Map.of("urlPrefix", "/season/detail/", "btnClass", "btn btn-sm btn-outline-info", "icon", "bi bi-eye", "text", "详情", "public", true));
        actions.add(Map.of("urlPrefix", "/season/edit/", "btnClass", "btn btn-sm btn-outline-warning", "icon", "bi bi-pencil", "text", "编辑"));
        actions.add(Map.of("urlPrefix", "/season/delete/", "method", "post", "btnClass", "btn btn-sm btn-outline-danger", "icon", "bi bi-trash", "text", "删除", "confirm", "确定要删除这个赛季吗？"));
        
        // 通用列表参数
        model.addAttribute("pageTitle", "赛季列表");
        model.addAttribute("pageIcon", "bi bi-calendar3");
        model.addAttribute("entityName", "赛季");
        model.addAttribute("addUrl", "/season/add");
        model.addAttribute("dataList", dataList);
        model.addAttribute("columns", columns);
        model.addAttribute("actions", actions);
        model.addAttribute("hasActions", true);
        model.addAttribute("emptyIcon", "bi bi-calendar-x");
        model.addAttribute("emptyMessage", "暂无赛季数据");
        
        // 检查管理员权限
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN") || a.getAuthority().equals("ROLE_ADMIN"));
        model.addAttribute("isAdmin", isAdmin);
        
        return "generic-list";
    }
    
    /**
     * 新增赛季页面（仅管理员）
     */
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @GetMapping("/add")
    public String addSeasonPage(Model model) {
        // 构建表单字段配置
        List<Map<String, Object>> fields = new ArrayList<>();
        
        fields.add(Map.of(
            "type", "number",
            "id", "year",
            "name", "year",
            "label", "年份",
            "placeholder", "如：2026",
            "min", "2000",
            "max", "2100",
            "required", true,
            "help", "赛季年份，如2026年"
        ));
        
        fields.add(Map.of(
            "type", "select",
            "id", "half",
            "name", "half",
            "label", "半年",
            "placeholder", "请选择",
            "required", true,
            "help", "选择上半年或下半年",
            "options", Arrays.asList(
                Map.of("value", "1", "text", "上半年"),
                Map.of("value", "2", "text", "下半年")
            )
        ));
        
        // 通用表单参数
        model.addAttribute("pageTitle", "添加赛季");
        model.addAttribute("pageIcon", "bi bi-calendar3");
        model.addAttribute("saveUrl", "/season/save");
        model.addAttribute("backUrl", "/season/management");
        model.addAttribute("formData", new Season());
        model.addAttribute("fields", fields);
        
        return "generic-form";
    }
    
    /**
     * 编辑赛季页面（仅管理员）
     */
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @GetMapping("/edit/{id}")
    public String editSeasonPage(@PathVariable Long id, Model model) {
        Season season = seasonService.getById(id);
        if (season == null) {
            return "redirect:/season/management";
        }
        
        // 构建表单字段配置
        List<Map<String, Object>> fields = new ArrayList<>();
        
        fields.add(Map.of(
            "type", "number",
            "id", "year",
            "name", "year",
            "label", "年份",
            "placeholder", "如：2026",
            "min", "2000",
            "max", "2100",
            "required", true,
            "help", "赛季年份，如2026年"
        ));
        
        fields.add(Map.of(
            "type", "select",
            "id", "half",
            "name", "half",
            "label", "半年",
            "placeholder", "请选择",
            "required", true,
            "help", "选择上半年或下半年",
            "options", Arrays.asList(
                Map.of("value", "1", "text", "上半年", "selected", season.getHalf() == 1),
                Map.of("value", "2", "text", "下半年", "selected", season.getHalf() == 2)
            )
        ));
        
        // 通用表单参数
        model.addAttribute("pageTitle", "编辑赛季");
        model.addAttribute("pageIcon", "bi bi-calendar3");
        model.addAttribute("saveUrl", "/season/save");
        model.addAttribute("backUrl", "/season/management");
        model.addAttribute("formData", season);
        model.addAttribute("fields", fields);
        
        return "generic-form";
    }
    
    @PostMapping("/save")
    public String saveSeason(@ModelAttribute Season season, RedirectAttributes redirectAttributes) {
        try {
            // 验证数据
            if (season.getYear() == null || season.getYear() < 2000 || season.getYear() > 2100) {
                redirectAttributes.addFlashAttribute("error", "年份必须在2000-2100之间");
                return "redirect:/season/add";
            }
            if (season.getHalf() == null || (season.getHalf() != 1 && season.getHalf() != 2)) {
                redirectAttributes.addFlashAttribute("error", "半年度只能选择1或2");
                return "redirect:/season/add";
            }
            
            // 检查是否已存在相同年份和半年度的赛季
            boolean exists = seasonService.lambdaQuery()
                    .eq(Season::getYear, season.getYear())
                    .eq(Season::getHalf, season.getHalf())
                    .ne(season.getId() != null, Season::getId, season.getId())
                    .exists();
            
            if (exists) {
                redirectAttributes.addFlashAttribute("error", "该年份和半年度的赛季已存在");
                return "redirect:/season/add";
            }
            
            if (season.getId() == null) {
                // 新增
                season.setCreatedAt(LocalDateTime.now());
                seasonService.save(season);
                redirectAttributes.addFlashAttribute("success", "赛季创建成功");
            } else {
                // 编辑
                seasonService.updateById(season);
                redirectAttributes.addFlashAttribute("success", "赛季更新成功");
            }
            
            // 赛季新增/编辑入口来自管理页，保存后回到管理页更符合预期
            return "redirect:/season/management";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "操作失败：" + e.getMessage());
            return season.getId() == null ? "redirect:/season/add" : "redirect:/season/edit/" + season.getId();
        }
    }
    
    /**
     * 删除赛季（仅管理员）
     */
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @PostMapping("/delete/{id}")
    public String deleteSeason(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            // 检查是否有相关联的系列赛事
            boolean hasRelatedSeries = seasonService.hasRelatedSeries(id);
            if (hasRelatedSeries) {
                redirectAttributes.addFlashAttribute("error", "该赛季下有关联的系列赛事，无法删除");
                return "redirect:/season/list";
            }
            
            seasonService.removeById(id);
            redirectAttributes.addFlashAttribute("success", "赛季删除成功");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "删除失败：" + e.getMessage());
        }
        return "redirect:/season/list";
    }
}