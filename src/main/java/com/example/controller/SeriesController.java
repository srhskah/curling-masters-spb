package com.example.controller;

import com.example.entity.*;
import com.example.service.*;
import com.example.util.IpAddressUtil;
import com.example.util.HtmlEscaper;
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
    // private TournamentLevelService tournamentLevelService;
    
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
        if (series == null) return "";
        if (series.getName() != null && !series.getName().trim().isEmpty()) return series.getName().trim();
        if (series.getSeasonId() == null || series.getSequence() == null) return "第?系列";
        long namedCount = seriesService.lambdaQuery()
                .eq(Series::getSeasonId, series.getSeasonId())
                .le(Series::getSequence, series.getSequence())
                .list()
                .stream()
                .filter(s -> s.getName() != null && !s.getName().trim().isEmpty())
                .count();
        int displayIdx = (int) (series.getSequence() - namedCount);
        if (displayIdx < 1) displayIdx = 1;
        return "第" + displayIdx + "系列";
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
        
        // 使用Map来存储额外信息
        List<Map<String, Object>> seriesInfoList = new ArrayList<>();
        for (Series s : seriesList) {
            Map<String, Object> seriesInfo = new HashMap<>();
            seriesInfo.put("series", s);
            
            Season season = seasonService.getById(s.getSeasonId());
            seriesInfo.put("seasonName", season != null ? season.getYear() + "年" + (season.getHalf() == 1 ? "上半年" : "下半年") : "未知");
            long tournamentCount = tournamentService.lambdaQuery().eq(com.example.entity.Tournament::getSeriesId, s.getId()).count();
            seriesInfo.put("tournamentCount", tournamentCount);
            
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
            
            item.put("data", Arrays.asList(
                series.getId(),
                "<strong>" + HtmlEscaper.escapeHtml(seriesName) + "</strong>",
                seasonName,
                series.getSequence(),
                "-", // Series表没有status字段，显示占位符
                "<span class=\"badge bg-info\">" + tournamentCount + " 个</span>",
                createTimeStr
            ));
            item.put("filters", Map.of("status", 0)); // 默认状态
            item.put("id", series.getId());
            
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
        columns.add(Map.of("title", "创建时间", "type", "text"));
        
        // 构建操作按钮
        List<Map<String, Object>> actions = new ArrayList<>();
        actions.add(Map.of("urlPrefix", "/season/detail/by-series/", "btnClass", "btn btn-sm btn-outline-info", "icon", "bi bi-eye", "text", "详情"));
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
        model.addAttribute("backUrl", seasonId != null ? "/season/detail/" + seasonId : "/series/list");
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
                seriesService.save(series);
                redirectAttributes.addFlashAttribute("success", "系列赛事创建成功");
            } else {
                seriesService.updateById(series);
                redirectAttributes.addFlashAttribute("success", "系列赛事更新成功");
            }
            
            return returnSeasonId != null ? "redirect:/season/detail/" + returnSeasonId : "redirect:/series/list";
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