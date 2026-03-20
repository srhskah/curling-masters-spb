package com.example.controller;

import com.example.entity.*;
import com.example.service.*;
import com.example.util.IpAddressUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
// import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/tournament")
public class TournamentController {

    @Autowired private SeasonService seasonService;
    @Autowired private ITournamentLevelService tournamentLevelService;
    @Autowired private SeriesService seriesService;
    @Autowired private TournamentService tournamentService;
    @Autowired private IMatchService matchService;
    @Autowired private ISetScoreService setScoreService;
    @Autowired private UserService userService;
    @Autowired private UserTournamentPointsService userTournamentPointsService;

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
    
    public boolean isHostUser(Long tournamentId) {
        User currentUser = getCurrentUser();
        if (currentUser == null) return false;
        Tournament tournament = tournamentService.getById(tournamentId);
        return tournament != null && tournament.getHostUserId().equals(currentUser.getId());
    }

    @GetMapping("/list")
    public String tournamentList(@RequestParam(required = false) Long seriesId,
                                 @RequestParam(required = false) Long seasonId,
                                 Model model, HttpServletRequest request) {
        String clientIp = IpAddressUtil.getClientIpAddress(request);
        String ipType = IpAddressUtil.classifyIpAddress(clientIp);
        model.addAttribute("clientIp", clientIp);
        model.addAttribute("ipType", ipType);
        
        User currentUser = getCurrentUser();
        boolean admin = isAdmin();
        
        List<Tournament> tournaments;
        if (seriesId != null) {
            tournaments = tournamentService.lambdaQuery().eq(Tournament::getSeriesId, seriesId).list();
        } else if (seasonId != null) {
            List<Series> seriesList = seriesService.lambdaQuery().eq(Series::getSeasonId, seasonId).list();
            List<Long> seriesIds = seriesList.stream().map(Series::getId).collect(Collectors.toList());
            tournaments = seriesIds.isEmpty()
                    ? List.of()
                    : tournamentService.lambdaQuery().in(Tournament::getSeriesId, seriesIds).list();
        } else {
            tournaments = tournamentService.list();
        }

        // ===== 预加载关联数据，计算“届次”（同赛季 + 同赛事等级的第 N 届）=====
        Map<Long, Series> seriesById = new HashMap<>();
        Map<Long, Season> seasonById = new HashMap<>();
        if (tournaments != null && !tournaments.isEmpty()) {
            List<Long> seriesIds = tournaments.stream()
                    .map(Tournament::getSeriesId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (!seriesIds.isEmpty()) {
                List<Series> seriesList = seriesService.listByIds(seriesIds);
                for (Series s : seriesList) {
                    seriesById.put(s.getId(), s);
                }
                List<Long> seasonIds = seriesList.stream()
                        .map(Series::getSeasonId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
                if (!seasonIds.isEmpty()) {
                    List<Season> seasons = seasonService.listByIds(seasonIds);
                    for (Season s : seasons) {
                        seasonById.put(s.getId(), s);
                    }
                }
            }
        }

        // tournamentId -> edition（届次）
        Map<Long, Integer> editionByTournamentId = new HashMap<>();
        if (tournaments != null && !tournaments.isEmpty()) {
            record GroupKey(Long seasonId, String levelCode) {}
            Map<GroupKey, List<Tournament>> groups = new HashMap<>();
            for (Tournament t : tournaments) {
                Series s = t.getSeriesId() != null ? seriesById.get(t.getSeriesId()) : null;
                Long sid = s != null ? s.getSeasonId() : null;
                if (sid == null || t.getLevelCode() == null) continue;
                groups.computeIfAbsent(new GroupKey(sid, t.getLevelCode()), k -> new ArrayList<>()).add(t);
            }
            for (Map.Entry<GroupKey, List<Tournament>> e : groups.entrySet()) {
                List<Tournament> list = e.getValue();
                list.sort(Comparator.comparing(Tournament::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
                for (int i = 0; i < list.size(); i++) {
                    Tournament t = list.get(i);
                    if (t.getId() != null) {
                        editionByTournamentId.put(t.getId(), i + 1);
                    }
                }
            }
        }

        // 修复：使用Map来存储额外信息，而不是直接调用不存在的set方法
        List<Map<String, Object>> tournamentInfoList = new ArrayList<>();
        for (Tournament t : tournaments) {
            Map<String, Object> tournamentInfo = new HashMap<>();
            tournamentInfo.put("tournament", t);

            Series series = t.getSeriesId() != null ? seriesById.get(t.getSeriesId()) : null;
            if (series == null && t.getSeriesId() != null) {
                series = seriesService.getById(t.getSeriesId());
                if (series != null) seriesById.put(series.getId(), series);
            }
            if (series != null) {
                Long sId = series.getSeasonId();
                Season season = sId != null ? seasonById.get(sId) : null;
                if (season == null && sId != null) {
                    season = seasonService.getById(sId);
                    if (season != null) seasonById.put(season.getId(), season);
                }
                tournamentInfo.put("seasonId", sId);
                tournamentInfo.put("seasonName", season != null ? season.getYear() + "年" + (season.getHalf() == 1 ? "上半年" : "下半年") : "");
                tournamentInfo.put("seasonYear", season != null ? season.getYear() : null);
                tournamentInfo.put("seasonHalf", season != null ? season.getHalf() : null);
                tournamentInfo.put("seriesName", buildSeriesDisplayName(series));
            }
            TournamentLevel level = tournamentLevelService.lambdaQuery()
                    .eq(TournamentLevel::getCode, t.getLevelCode())
                    .one();
            tournamentInfo.put("levelName", level != null ? level.getName() : t.getLevelCode());
            User hostUser = userService.getById(t.getHostUserId());
            tournamentInfo.put("hostUserName", hostUser != null ? hostUser.getUsername() : "未知");
            tournamentInfo.put("edition", t.getId() != null ? editionByTournamentId.get(t.getId()) : null);
            
            tournamentInfoList.add(tournamentInfo);
        }

        // 按“赛季 -> 等级 -> 届次”排序（便于列表直观看届次）
        tournamentInfoList.sort((a, b) -> {
            Integer ay = (Integer) a.get("seasonYear");
            Integer by = (Integer) b.get("seasonYear");
            int c = Comparator.nullsLast(Comparator.<Integer>naturalOrder()).reversed().compare(ay, by);
            if (c != 0) return c;
            Integer ah = (Integer) a.get("seasonHalf");
            Integer bh = (Integer) b.get("seasonHalf");
            c = Comparator.nullsLast(Comparator.<Integer>naturalOrder()).reversed().compare(ah, bh);
            if (c != 0) return c;
            String al = (String) a.get("levelName");
            String bl = (String) b.get("levelName");
            c = Comparator.nullsLast(String::compareTo).compare(al, bl);
            if (c != 0) return c;
            Integer ae = (Integer) a.get("edition");
            Integer be = (Integer) b.get("edition");
            return Comparator.nullsLast(Comparator.<Integer>naturalOrder()).compare(ae, be);
        });
        
        // 构建通用列表数据
        List<Map<String, Object>> dataList = new ArrayList<>();
        for (Map<String, Object> tournamentInfo : tournamentInfoList) {
            Tournament tournament = (Tournament) tournamentInfo.get("tournament");
            String seasonName = (String) tournamentInfo.get("seasonName");
            String seriesName = (String) tournamentInfo.get("seriesName");
            String levelName = (String) tournamentInfo.get("levelName");
            String hostUserName = (String) tournamentInfo.get("hostUserName");
            Integer edition = (Integer) tournamentInfo.get("edition");
            
            Map<String, Object> item = new HashMap<>();
            
            // 构建状态徽章
            String statusBadge = "";
            switch (tournament.getStatus()) {
                case 0:
                    statusBadge = "<span class=\"badge bg-secondary\">筹备中</span>";
                    break;
                case 1:
                    statusBadge = "<span class=\"badge bg-primary\">进行中</span>";
                    break;
                case 2:
                    statusBadge = "<span class=\"badge bg-success\">已结束</span>";
                    break;
            }
            
            // 构建日期显示
            String dateRange = "";
            if (tournament.getStartDate() != null && tournament.getEndDate() != null) {
                dateRange = tournament.getStartDate() + " 至 " + tournament.getEndDate();
            } else if (tournament.getStartDate() != null) {
                dateRange = tournament.getStartDate() + " 起";
            } else {
                dateRange = "-";
            }
            
            // 构建创建时间显示
            String createTimeStr = tournament.getCreatedAt() != null ?
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(tournament.getCreatedAt()) : "-";
            
            item.put("data", Arrays.asList(
                tournament.getId(),
                "<strong>" + levelName + "</strong>" +
                        (edition != null ? " <span class=\"badge bg-light text-dark border\">第" + edition + "届</span>" : ""),
                seriesName,
                seasonName,
                hostUserName,
                statusBadge,
                dateRange,
                createTimeStr
            ));
            item.put("filters", Map.of("status", tournament.getStatus()));
            item.put("id", tournament.getId());
            
            dataList.add(item);
        }
        
        // 构建列配置
        List<Map<String, Object>> columns = new ArrayList<>();
        columns.add(Map.of("title", "ID", "type", "text"));
        columns.add(Map.of("title", "赛事等级", "type", "custom"));
        columns.add(Map.of("title", "赛事系列", "type", "text"));
        columns.add(Map.of("title", "所属赛季", "type", "text"));
        columns.add(Map.of("title", "主办人", "type", "text"));
        columns.add(Map.of("title", "状态", "type", "custom"));
        columns.add(Map.of("title", "举办时间", "type", "custom"));
        columns.add(Map.of("title", "创建时间", "type", "text"));
        
        // 构建操作按钮
        List<Map<String, Object>> actions = new ArrayList<>();
        actions.add(Map.of("urlPrefix", "/tournament/detail/", "btnClass", "btn btn-sm btn-outline-info", "icon", "bi bi-eye", "text", "详情", "public", true));
        actions.add(Map.of("urlPrefix", "/tournament/edit/", "btnClass", "btn btn-sm btn-outline-warning", "icon", "bi bi-pencil", "text", "编辑"));
        actions.add(Map.of("urlPrefix", "/tournament/delete/", "method", "post", "btnClass", "btn btn-sm btn-outline-danger", "icon", "bi bi-trash", "text", "删除", "confirm", "确定要删除这个赛事吗？"));
        
        // 通用列表参数
        model.addAttribute("pageTitle", "赛事列表");
        model.addAttribute("pageIcon", "bi bi-trophy");
        model.addAttribute("entityName", "赛事");
        model.addAttribute("addUrl", "/tournament/add");
        model.addAttribute("dataList", dataList);
        model.addAttribute("columns", columns);
        model.addAttribute("actions", actions);
        model.addAttribute("hasActions", true);
        model.addAttribute("emptyIcon", "bi bi-trophy");
        model.addAttribute("emptyMessage", "暂无赛事数据");
        model.addAttribute("isAdmin", admin);
        model.addAttribute("currentUser", currentUser);
        
        return "generic-list";
    }
    
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @GetMapping("/add")
    public String addTournamentPage(@RequestParam(required = false) Long seriesId, Model model, HttpServletRequest request) {
        String clientIp = IpAddressUtil.getClientIpAddress(request);
        String ipType = IpAddressUtil.classifyIpAddress(clientIp);
        model.addAttribute("clientIp", clientIp);
        model.addAttribute("ipType", ipType);
        
        List<Series> allSeries = seriesService.list();
        // 修复：使用Map来存储显示名称
        List<Map<String, Object>> seriesDisplayList = new ArrayList<>();
        for (Series s : allSeries) {
            Map<String, Object> seriesInfo = new HashMap<>();
            seriesInfo.put("series", s);
            
            Season season = seasonService.getById(s.getSeasonId());
            String displayName = (season != null ? season.getYear() + "年" : "") + 
                (season != null ? (season.getHalf() == 1 ? "上半年" : "下半年") : "") + 
                " - 第" + s.getSequence() + "系列" + (s.getName() != null ? "(" + s.getName() + ")" : "");
            seriesInfo.put("displayName", displayName);
            
            seriesDisplayList.add(seriesInfo);
        }
        
        List<TournamentLevel> levels = tournamentLevelService.list();
        // 主办人：所有“计入总排名”的用户，而不仅限管理员
        List<User> hostCandidates = userService.lambdaQuery()
                .eq(User::getIncludeInTotalRanking, Boolean.TRUE)
                .list();

        Series selectedSeries = seriesId != null ? seriesService.getById(seriesId) : null;
        Long returnSeasonId = selectedSeries != null ? selectedSeries.getSeasonId() : null;
        
        // 构建系列选项
        List<Map<String, Object>> seriesOptions = new ArrayList<>();
        for (Map<String, Object> seriesInfo : seriesDisplayList) {
            Series series = (Series) seriesInfo.get("series");
            String displayName = (String) seriesInfo.get("displayName");
            seriesOptions.add(Map.of(
                "value", series.getId(),
                "text", displayName,
                "selected", series.getId().equals(seriesId)
            ));
        }
        
        // 构建等级选项
        List<Map<String, Object>> levelOptions = new ArrayList<>();
        for (TournamentLevel level : levels) {
            levelOptions.add(Map.of(
                "value", level.getCode(),
                "text", level.getName(),
                "defaultChampionPointsRatio", level.getDefaultChampionRatio()
            ));
        }
        
        // 构建主办人选项
        List<Map<String, Object>> hostOptions = new ArrayList<>();
        for (User u : hostCandidates) {
            hostOptions.add(Map.of(
                "value", u.getId(),
                "text", u.getUsername()
            ));
        }
        
        // 构建表单字段配置
        List<Map<String, Object>> fields = new ArrayList<>();
        
        fields.add(Map.of(
            "type", "select",
            "id", "seriesId",
            "name", "seriesId",
            "label", "赛事系列",
            "placeholder", "请选择赛事系列",
            "required", true,
            "help", "选择该赛事所属的系列",
            "options", seriesOptions
        ));
        
        fields.add(Map.of(
            "type", "select",
            "id", "levelCode",
            "name", "levelCode",
            "label", "赛事等级",
            "placeholder", "请选择等级",
            "required", true,
            "help", "选择赛事的等级",
            "options", levelOptions
        ));
        
        fields.add(Map.of(
            "type", "select",
            "id", "hostUserId",
            "name", "hostUserId",
            "label", "主办人",
            "placeholder", "请选择主办人",
            "required", true,
            "help", "选择赛事的主办人",
            "options", hostOptions
        ));
        
        fields.add(Map.of(
            "type", "number",
            "id", "championPointsRatio",
            "name", "championPointsRatio",
            "label", "冠军积分/人数比率",
            "placeholder", "如：60（参赛人数×60=冠军积分）",
            "min", "0",
            "max", "1000",
            "step", "0.01",
            "required", true,
            "help", "含义：冠军积分 = 参赛人数 × 比率。选择赛事等级后将自动填充默认值，仍可手动修改。"
        ));
        
        fields.add(Map.of(
            "type", "select",
            "id", "status",
            "name", "status",
            "label", "状态",
            "placeholder", "请选择",
            "required", true,
            "help", "设置当前赛事的状态",
            "options", Arrays.asList(
                Map.of("value", "0", "text", "筹备中"),
                Map.of("value", "1", "text", "进行中"),
                Map.of("value", "2", "text", "已结束")
            )
        ));
        
        Map<String, Object> dateFields = Map.of(
            "type", "row",
            "fields", Arrays.asList(
                Map.of(
                    "type", "date",
                    "id", "startDate",
                    "name", "startDate",
                    "label", "开始日期",
                    "colClass", "col-md-6",
                    "help", "赛事开始日期（可选）"
                ),
                Map.of(
                    "type", "date",
                    "id", "endDate",
                    "name", "endDate",
                    "label", "结束日期",
                    "colClass", "col-md-6",
                    "help", "赛事结束日期（可选）"
                )
            )
        );
        fields.add(dateFields);
        
        // 通用表单参数
        model.addAttribute("pageTitle", "添加赛事");
        model.addAttribute("pageIcon", "bi bi-trophy");
        model.addAttribute("saveUrl", returnSeasonId != null ? "/tournament/save?returnSeasonId=" + returnSeasonId : "/tournament/save");
        model.addAttribute("backUrl", returnSeasonId != null ? "/season/detail/" + returnSeasonId : "/tournament/list");
        Tournament formData = new Tournament();
        formData.setSeriesId(seriesId);
        model.addAttribute("formData", formData);
        model.addAttribute("fields", fields);
        model.addAttribute("clientIp", clientIp);
        model.addAttribute("ipType", ipType);

        model.addAttribute("customScript", """
(() => {
  const levelSelect = document.getElementById('levelCode');
  const ratioInput = document.getElementById('championPointsRatio');
  if (!levelSelect || !ratioInput) return;

  const applyDefault = () => {
    const opt = levelSelect.options[levelSelect.selectedIndex];
    if (!opt) return;
    const v = opt.getAttribute('data-default-champion-points-ratio');
    if (v !== null && v !== undefined && String(v).trim() !== '') {
      ratioInput.value = v;
    }
  };

  levelSelect.addEventListener('change', applyDefault);
  if (levelSelect.value) applyDefault();
})();
""");
        
        return "generic-form";
    }
    
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or @tournamentController.isHostUser(#id)")
    @GetMapping("/edit/{id}")
    public String editTournamentPage(@PathVariable Long id, Model model, HttpServletRequest request) {
        String clientIp = IpAddressUtil.getClientIpAddress(request);
        String ipType = IpAddressUtil.classifyIpAddress(clientIp);
        model.addAttribute("clientIp", clientIp);
        model.addAttribute("ipType", ipType);
        
        Tournament tournament = tournamentService.getById(id);
        if (tournament == null) {
            return "redirect:/tournament/list";
        }
        
        // User currentUser = getCurrentUser();
        boolean admin = isAdmin();
        boolean isHost = isHostUser(id);
        
        if (!admin && !isHost) {
            return "redirect:/tournament/list";
        }
        
        List<Series> allSeries = seriesService.list();
        List<Map<String, Object>> seriesDisplayList = new ArrayList<>();
        for (Series s : allSeries) {
            Map<String, Object> seriesInfo = new HashMap<>();
            seriesInfo.put("series", s);
            
            Season season = seasonService.getById(s.getSeasonId());
            String displayName = (season != null ? season.getYear() + "年" : "") + 
                (season != null ? (season.getHalf() == 1 ? "上半年" : "下半年") : "") + 
                " - 第" + s.getSequence() + "系列" + (s.getName() != null ? "(" + s.getName() + ")" : "");
            seriesInfo.put("displayName", displayName);
            
            seriesDisplayList.add(seriesInfo);
        }
        
        List<TournamentLevel> levels = tournamentLevelService.list();
        // 主办人：所有“计入总排名”的用户
        List<User> hostCandidates = userService.lambdaQuery()
                .eq(User::getIncludeInTotalRanking, Boolean.TRUE)
                .list();

        Series selectedSeries = tournament.getSeriesId() != null ? seriesService.getById(tournament.getSeriesId()) : null;
        Long returnSeasonId = selectedSeries != null ? selectedSeries.getSeasonId() : null;
        
        // 构建系列选项
        List<Map<String, Object>> seriesOptions = new ArrayList<>();
        for (Map<String, Object> seriesInfo : seriesDisplayList) {
            Series series = (Series) seriesInfo.get("series");
            String displayName = (String) seriesInfo.get("displayName");
            seriesOptions.add(Map.of(
                "value", series.getId(),
                "text", displayName,
                "selected", series.getId().equals(tournament.getSeriesId())
            ));
        }
        
        // 构建等级选项
        List<Map<String, Object>> levelOptions = new ArrayList<>();
        for (TournamentLevel level : levels) {
            levelOptions.add(Map.of(
                "value", level.getCode(),
                "text", level.getName(),
                "selected", level.getCode().equals(tournament.getLevelCode()),
                "defaultChampionPointsRatio", level.getDefaultChampionRatio()
            ));
        }
        
        // 构建主办人选项
        List<Map<String, Object>> hostOptions = new ArrayList<>();
        for (User u : hostCandidates) {
            hostOptions.add(Map.of(
                "value", u.getId(),
                "text", u.getUsername(),
                "selected", u.getId().equals(tournament.getHostUserId())
            ));
        }
        
        // 构建表单字段配置
        List<Map<String, Object>> fields = new ArrayList<>();
        
        fields.add(Map.of(
            "type", "select",
            "id", "seriesId",
            "name", "seriesId",
            "label", "赛事系列",
            "placeholder", "请选择赛事系列",
            "required", true,
            "help", "选择该赛事所属的系列",
            "options", seriesOptions
        ));
        
        fields.add(Map.of(
            "type", "select",
            "id", "levelCode",
            "name", "levelCode",
            "label", "赛事等级",
            "placeholder", "请选择等级",
            "required", true,
            "help", "选择赛事的等级",
            "options", levelOptions
        ));
        
        fields.add(Map.of(
            "type", "select",
            "id", "hostUserId",
            "name", "hostUserId",
            "label", "主办人",
            "placeholder", "请选择主办人",
            "required", true,
            "help", "选择赛事的主办人",
            "options", hostOptions
        ));
        
        fields.add(Map.of(
            "type", "number",
            "id", "championPointsRatio",
            "name", "championPointsRatio",
            "label", "冠军积分/人数比率",
            "placeholder", "如：60（参赛人数×60=冠军积分）",
            "min", "0",
            "max", "1000",
            "step", "0.01",
            "required", true,
            "help", "含义：冠军积分 = 参赛人数 × 比率。选择赛事等级后将自动填充默认值，仍可手动修改。"
        ));
        
        fields.add(Map.of(
            "type", "select",
            "id", "status",
            "name", "status",
            "label", "状态",
            "placeholder", "请选择",
            "required", true,
            "help", "设置当前赛事的状态",
            "options", Arrays.asList(
                Map.of("value", "0", "text", "筹备中", "selected", tournament.getStatus() == 0),
                Map.of("value", "1", "text", "进行中", "selected", tournament.getStatus() == 1),
                Map.of("value", "2", "text", "已结束", "selected", tournament.getStatus() == 2)
            )
        ));
        
        Map<String, Object> dateFields = Map.of(
            "type", "row",
            "fields", Arrays.asList(
                Map.of(
                    "type", "date",
                    "id", "startDate",
                    "name", "startDate",
                    "label", "开始日期",
                    "colClass", "col-md-6",
                    "help", "赛事开始日期（可选）"
                ),
                Map.of(
                    "type", "date",
                    "id", "endDate",
                    "name", "endDate",
                    "label", "结束日期",
                    "colClass", "col-md-6",
                    "help", "赛事结束日期（可选）"
                )
            )
        );
        fields.add(dateFields);
        
        // 通用表单参数
        model.addAttribute("pageTitle", "编辑赛事");
        model.addAttribute("pageIcon", "bi bi-trophy");
        model.addAttribute("saveUrl", returnSeasonId != null ? "/tournament/save?returnSeasonId=" + returnSeasonId : "/tournament/save");
        model.addAttribute("backUrl", returnSeasonId != null ? "/season/detail/" + returnSeasonId : "/tournament/list");
        model.addAttribute("formData", tournament);
        model.addAttribute("fields", fields);
        model.addAttribute("clientIp", clientIp);
        model.addAttribute("ipType", ipType);

        model.addAttribute("customScript", """
(() => {
  const levelSelect = document.getElementById('levelCode');
  const ratioInput = document.getElementById('championPointsRatio');
  if (!levelSelect || !ratioInput) return;

  const applyDefault = () => {
    const opt = levelSelect.options[levelSelect.selectedIndex];
    if (!opt) return;
    const v = opt.getAttribute('data-default-champion-points-ratio');
    if (v !== null && v !== undefined && String(v).trim() !== '') {
      ratioInput.value = v;
    }
  };

  levelSelect.addEventListener('change', applyDefault);
})();
""");
        
        return "generic-form";
    }
    
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or @tournamentController.isHostUser(#tournament.id)")
    @PostMapping("/save")
    public String saveTournament(@ModelAttribute Tournament tournament,
                                  @RequestParam(required = false) BigDecimal championPointsRatio,
                                  @RequestParam(required = false) Long returnSeasonId,
                                  RedirectAttributes redirectAttributes) {
        try {
            if (tournament.getSeriesId() == null || tournament.getLevelCode() == null || 
                tournament.getHostUserId() == null) {
                redirectAttributes.addFlashAttribute("error", "请填写所有必填字段");
                return "redirect:/tournament/add" + (tournament.getSeriesId() != null ? "?seriesId=" + tournament.getSeriesId() : "");
            }
            
            TournamentLevel level = tournamentLevelService.lambdaQuery()
                    .eq(TournamentLevel::getCode, tournament.getLevelCode())
                    .one();
            if (championPointsRatio != null) {
                tournament.setChampionPointsRatio(championPointsRatio);
            } else if (level != null && tournament.getId() == null) {
                tournament.setChampionPointsRatio(level.getDefaultChampionRatio());
            }
            
            if (tournament.getId() == null) {
                // 新增赛事时：如果表单已选择状态（如“已结束”），不要被默认值覆盖
                if (tournament.getStatus() == null) {
                    tournament.setStatus(0);
                }
                tournamentService.save(tournament);
                redirectAttributes.addFlashAttribute("success", "赛事创建成功");
            } else {
                tournamentService.updateById(tournament);
                redirectAttributes.addFlashAttribute("success", "赛事更新成功");
            }
            
            return returnSeasonId != null ? "redirect:/season/detail/" + returnSeasonId : "redirect:/tournament/list";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "操作失败：" + e.getMessage());
            return tournament.getId() == null ? "redirect:/tournament/add" : "redirect:/tournament/edit/" + tournament.getId();
        }
    }
    
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or @tournamentController.isHostUser(#id)")
    @PostMapping("/delete/{id}")
    public String deleteTournament(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            // User currentUser = getCurrentUser();
            boolean admin = isAdmin();
            boolean isHost = isHostUser(id);
            
            if (!admin && !isHost) {
                redirectAttributes.addFlashAttribute("error", "您没有权限删除此赛事");
                return "redirect:/tournament/list";
            }
            
            tournamentService.removeById(id);
            redirectAttributes.addFlashAttribute("success", "赛事删除成功");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "删除失败：" + e.getMessage());
        }
        return "redirect:/tournament/list";
    }
    
    @GetMapping("/detail/{id}")
    public String tournamentDetail(@PathVariable Long id, Model model, HttpServletRequest request) {
        String clientIp = IpAddressUtil.getClientIpAddress(request);
        String ipType = IpAddressUtil.classifyIpAddress(clientIp);
        model.addAttribute("clientIp", clientIp);
        model.addAttribute("ipType", ipType);
        
        Tournament tournament = tournamentService.getById(id);
        if (tournament == null) {
            return "redirect:/tournament/list";
        }
        
        Series series = seriesService.getById(tournament.getSeriesId());
        Season season = series != null ? seasonService.getById(series.getSeasonId()) : null;
        TournamentLevel level = tournamentLevelService.lambdaQuery()
                .eq(TournamentLevel::getCode, tournament.getLevelCode())
                .one();
        User hostUser = userService.getById(tournament.getHostUserId());
        
        // 修复：使用Map存储额外信息
        Map<String, Object> tournamentInfo = new HashMap<>();
        tournamentInfo.put("tournament", tournament);
        tournamentInfo.put("seasonId", series != null ? series.getSeasonId() : null);
        tournamentInfo.put("seasonName", season != null ? season.getYear() + "年" + (season.getHalf() == 1 ? "上半年" : "下半年") : "");
        tournamentInfo.put("seriesName", series != null ? buildSeriesDisplayName(series) : "");
        tournamentInfo.put("levelName", level != null ? level.getName() : tournament.getLevelCode());
        tournamentInfo.put("hostUserName", hostUser != null ? hostUser.getUsername() : "未知");

        // 计算届次：同赛季 + 同赛事等级内，第 N 届（按创建时间升序）
        Integer edition = null;
        if (series != null && series.getSeasonId() != null && tournament.getLevelCode() != null) {
            List<Series> seasonSeries = seriesService.lambdaQuery()
                    .eq(Series::getSeasonId, series.getSeasonId())
                    .list();
            List<Long> seasonSeriesIds = seasonSeries.stream()
                    .map(Series::getId)
                    .filter(Objects::nonNull)
                    .toList();
            if (!seasonSeriesIds.isEmpty()) {
                List<Tournament> sameGroup = tournamentService.lambdaQuery()
                        .in(Tournament::getSeriesId, seasonSeriesIds)
                        .eq(Tournament::getLevelCode, tournament.getLevelCode())
                        .list();
                sameGroup.sort(Comparator.comparing(Tournament::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
                for (int i = 0; i < sameGroup.size(); i++) {
                    Tournament tt = sameGroup.get(i);
                    if (tt.getId() != null && tt.getId().equals(tournament.getId())) {
                        edition = i + 1;
                        break;
                    }
                }
            }
        }
        tournamentInfo.put("edition", edition);

        // ===== 新增：同系列 tabs + 同级别上下一个赛事导航 =====
        // 1) 同系列所有赛事（用于 tabs）
        List<Tournament> seriesTournaments = tournamentService.lambdaQuery()
                .eq(Tournament::getSeriesId, tournament.getSeriesId())
                .orderByAsc(Tournament::getCreatedAt)
                .list();

        // 2) 同级别上下一个赛事（用于箭头导航）
        // 切换规则：先看赛季（year + half），再看该赛季内系列的 sequence。
        Tournament prevSameLevelTournament = null;
        Tournament nextSameLevelTournament = null;
        if (tournament.getLevelCode() != null && tournament.getSeriesId() != null) {
            List<Tournament> levelTournaments = tournamentService.lambdaQuery()
                    .eq(Tournament::getLevelCode, tournament.getLevelCode())
                    .orderByAsc(Tournament::getCreatedAt)
                    .list();

            // 同一个 seriesId 内可能存在多场同等级（一般应为1场）；这里取最早的一场作为代表，用于“按系列切换”
            Map<Long, Tournament> tournamentBySeriesId = new HashMap<>();
            for (Tournament t : levelTournaments) {
                if (t == null || t.getSeriesId() == null) continue;
                tournamentBySeriesId.putIfAbsent(t.getSeriesId(), t);
            }

            Set<Long> levelSeriesIds = tournamentBySeriesId.keySet();
            if (!levelSeriesIds.isEmpty()) {
                List<Series> levelSeriesList = seriesService.listByIds(new ArrayList<>(levelSeriesIds));
                Set<Long> levelSeasonIds = levelSeriesList.stream()
                        .map(Series::getSeasonId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

                Map<Long, Season> levelSeasonById = levelSeasonIds.isEmpty()
                        ? Map.of()
                        : seasonService.listByIds(new ArrayList<>(levelSeasonIds)).stream()
                        .collect(Collectors.toMap(Season::getId, s -> s, (a, b) -> a));

                levelSeriesList.sort(Comparator
                        .comparing((Series s) -> {
                            Season se = levelSeasonById.get(s.getSeasonId());
                            return se != null ? se.getYear() : Integer.MIN_VALUE;
                        })
                        .thenComparing(s -> {
                            Season se = levelSeasonById.get(s.getSeasonId());
                            return se != null ? se.getHalf() : Integer.MIN_VALUE;
                        })
                        .thenComparing(Series::getSequence, Comparator.nullsLast(Comparator.naturalOrder())));

                int currentIdx = -1;
                for (int i = 0; i < levelSeriesList.size(); i++) {
                    Series s = levelSeriesList.get(i);
                    if (s != null && tournament.getSeriesId().equals(s.getId())) {
                        currentIdx = i;
                        break;
                    }
                }

                if (currentIdx > 0) {
                    Long prevSeriesId = levelSeriesList.get(currentIdx - 1).getId();
                    prevSameLevelTournament = prevSeriesId != null ? tournamentBySeriesId.get(prevSeriesId) : null;
                }
                if (currentIdx >= 0 && currentIdx < levelSeriesList.size() - 1) {
                    Long nextSeriesId = levelSeriesList.get(currentIdx + 1).getId();
                    nextSameLevelTournament = nextSeriesId != null ? tournamentBySeriesId.get(nextSeriesId) : null;
                }
            }
        }

        // 预加载：等级 code -> levelName
        Map<String, TournamentLevel> levelByCode = tournamentLevelService.list().stream()
                .filter(l -> l.getCode() != null)
                .collect(Collectors.toMap(TournamentLevel::getCode, l -> l, (a, b) -> a));

        // 预加载：seriesId -> seasonId，及 seasonId -> seasonName
        Set<Long> seriesIdsToLoad = new HashSet<>();
        for (Tournament t : seriesTournaments) {
            if (t != null && t.getSeriesId() != null) seriesIdsToLoad.add(t.getSeriesId());
        }
        if (prevSameLevelTournament != null && prevSameLevelTournament.getSeriesId() != null) {
            seriesIdsToLoad.add(prevSameLevelTournament.getSeriesId());
        }
        if (nextSameLevelTournament != null && nextSameLevelTournament.getSeriesId() != null) {
            seriesIdsToLoad.add(nextSameLevelTournament.getSeriesId());
        }

        Map<Long, Series> seriesById = seriesIdsToLoad.isEmpty()
                ? Map.of()
                : seriesService.listByIds(seriesIdsToLoad).stream()
                .collect(Collectors.toMap(Series::getId, s -> s, (a, b) -> a));

        Set<Long> seasonIdsToLoad = seriesById.values().stream()
                .map(Series::getSeasonId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Season> seasonById = seasonIdsToLoad.isEmpty()
                ? Map.of()
                : seasonService.listByIds(seasonIdsToLoad).stream()
                .collect(Collectors.toMap(Season::getId, s -> s, (a, b) -> a));

        // 预计算：{tournamentId -> edition}（用于 tabs 文案/箭头文案）
        Map<Long, Integer> editionByTournamentId = new HashMap<>();
        // keys: seasonId|levelCode
        Set<String> editionKeys = new HashSet<>();
        for (Tournament t : seriesTournaments) {
            if (t == null || t.getLevelCode() == null) continue;
            Series ts = seriesById.get(t.getSeriesId());
            if (ts == null || ts.getSeasonId() == null) continue;
            editionKeys.add(ts.getSeasonId() + "|" + t.getLevelCode());
        }
        // 注意：prev/next 可能为 null，不能直接用 List.of(...) 包含 null
        List<Tournament> prevNext = new ArrayList<>();
        if (prevSameLevelTournament != null) prevNext.add(prevSameLevelTournament);
        if (nextSameLevelTournament != null) prevNext.add(nextSameLevelTournament);
        for (Tournament t : prevNext) {
            if (t == null || t.getLevelCode() == null) continue;
            Series ts = seriesById.get(t.getSeriesId());
            if (ts == null || ts.getSeasonId() == null) continue;
            editionKeys.add(ts.getSeasonId() + "|" + t.getLevelCode());
        }

        for (String key : editionKeys) {
            String[] sp = key.split("\\|", 2);
            if (sp.length != 2) continue;
            Long seasonId = Long.valueOf(sp[0]);
            String lc = sp[1];

            // 同赛季的所有系列
            List<Series> seasonSeries = seriesService.lambdaQuery()
                    .eq(Series::getSeasonId, seasonId)
                    .list();
            List<Long> seasonSeriesIds = seasonSeries.stream()
                    .map(Series::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (seasonSeriesIds.isEmpty()) continue;

            List<Tournament> sameGroup = tournamentService.lambdaQuery()
                    .in(Tournament::getSeriesId, seasonSeriesIds)
                    .eq(Tournament::getLevelCode, lc)
                    .list();
            sameGroup.sort(Comparator.comparing(Tournament::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
            for (int i = 0; i < sameGroup.size(); i++) {
                Tournament tt = sameGroup.get(i);
                if (tt != null && tt.getId() != null) {
                    editionByTournamentId.put(tt.getId(), i + 1);
                }
            }
        }

        // tabs 数据：以“赛事等级”为单位去重展示（同一等级可能有多届/多场，但 tab 只显示该等级有哪些）
        String currentLevelCode = tournament.getLevelCode();
        Map<String, Tournament> repByLevelCode = new LinkedHashMap<>();
        for (Tournament t : seriesTournaments) {
            if (t == null || t.getLevelCode() == null) continue;
            String lc = t.getLevelCode();
            // 默认取该等级在 series 内出现的第一场；若当前赛事本身属于该等级，则用当前赛事作为代表
            if (!repByLevelCode.containsKey(lc) || (t.getId() != null && t.getId().equals(tournament.getId()))) {
                repByLevelCode.put(lc, t);
            }
        }

        List<Map<String, Object>> seriesTournamentTabs = new ArrayList<>();
        Map<String, String> finalsTypeByLevelCode = new HashMap<>();
        finalsTypeByLevelCode.put("CM Fi-A", "赛季总决赛（A）");
        finalsTypeByLevelCode.put("CM Fi-B", "赛季总决赛（B）");
        finalsTypeByLevelCode.put("CM An-Fi", "年终总决赛");
        for (Map.Entry<String, Tournament> e : repByLevelCode.entrySet()) {
            String lc = e.getKey();
            Tournament t = e.getValue();
            TournamentLevel tl = lc != null ? levelByCode.get(lc) : null;
            String levelName = tl != null && tl.getName() != null ? tl.getName() : (lc != null ? lc : "");
            Integer ed = (t != null && t.getId() != null) ? editionByTournamentId.get(t.getId()) : null;
            // 选项卡：
            // - 赛季总决赛（A/B）、年终总决赛：{赛事等级}（不加任何其它文字）
            // - 其它赛事等级：{赛事等级}-{对应赛事等级在本赛季的届次}（去掉“第/届”）
            String tabLabel;
            if (lc != null && finalsTypeByLevelCode.containsKey(lc)) {
                tabLabel = levelName;
            } else {
                tabLabel = ed != null ? (levelName + "-" + ed) : levelName;
            }

            seriesTournamentTabs.add(Map.of(
                    "id", t != null ? t.getId() : null,
                    "label", tabLabel,
                    "active", lc != null && currentLevelCode != null && lc.equals(currentLevelCode)
            ));
        }

        // 箭头文案（按宽屏/窄屏分别准备）
        Long currentSeasonId = series != null ? series.getSeasonId() : null;
        String currentSeasonName = season != null
                ? (season.getYear() + "年" + (season.getHalf() == 1 ? "上半年" : "下半年"))
                : "";

        Function<Tournament, Map<String, Object>> buildNavMap = (Tournament t) -> {
            if (t == null) return null;
            Series ts = seriesById.get(t.getSeriesId());
            Long sid = ts != null ? ts.getSeasonId() : null;
            Season se = sid != null ? seasonById.get(sid) : null;
            String seasonName = se != null
                    ? (se.getYear() + "年" + (se.getHalf() == 1 ? "上半年" : "下半年"))
                    : "";
            String lc = t.getLevelCode();
            TournamentLevel tl = lc != null ? levelByCode.get(lc) : null;
            String levelName = tl != null && tl.getName() != null ? tl.getName() : (lc != null ? lc : "");
            Integer ed = editionByTournamentId.get(t.getId());
            boolean crossSeason = currentSeasonId != null && sid != null && !currentSeasonId.equals(sid);
            boolean isFinalsType = lc != null && finalsTypeByLevelCode.containsKey(lc);

            String desktopText;
            if (isFinalsType) {
                // 赛季总决赛（A/B）、年终总决赛
                if (!crossSeason) {
                    // 不跨赛季：只显示 {赛事等级}
                    desktopText = levelName;
                } else {
                    // 跨赛季：A/B 显示赛季；年终显示年份
                    String finalsPrefix = finalsTypeByLevelCode.get(lc);
                    if ("CM An-Fi".equals(lc)) {
                        // desktopText = finalsPrefix + "：" + (se != null ? se.getYear() : "");
                        desktopText = se != null ? se.getYear().toString() : "";
                    } else {
                        // desktopText = finalsPrefix + "：" + seasonName;
                        desktopText = seasonName;
                    }
                }
            } else {
                // 其它赛事等级
                String edText = ed != null ? ed.toString() : "";
                if (!crossSeason) {
                    // 不跨赛季：{赛事等级}-{届次}（无“第/届”）
                    desktopText = edText.isEmpty() ? levelName : (levelName + "-" + edText);
                } else {
                    // 跨赛季：{赛季}-{赛事等级}-{届次}（无“第/届”）
                    desktopText = edText.isEmpty() ? (seasonName + "-" + levelName) : (seasonName + "-" + levelName + "-" + edText);
                }
            }

            // 跨赛季要求：不管宽屏/窄屏都呈现 desktopText
            String mobileText = crossSeason ? desktopText : desktopText;

            return Map.of(
                    "id", t.getId(),
                    "desktopText", desktopText,
                    "mobileText", mobileText
            );
        };

        model.addAttribute("seriesTournamentTabs", seriesTournamentTabs);
        model.addAttribute("prevSameLevelTournamentNav", buildNavMap.apply(prevSameLevelTournament));
        model.addAttribute("nextSameLevelTournamentNav", buildNavMap.apply(nextSameLevelTournament));
        
        List<Match> matches = matchService.lambdaQuery().eq(Match::getTournamentId, id).orderByAsc(Match::getRound).list();
        List<Map<String, Object>> matchInfoList = new ArrayList<>();
        for (Match m : matches) {
            Map<String, Object> matchInfo = new HashMap<>();
            matchInfo.put("match", m);
            
            User player1 = userService.getById(m.getPlayer1Id());
            User player2 = userService.getById(m.getPlayer2Id());
            matchInfo.put("player1Name", player1 != null ? player1.getUsername() : "待定");
            matchInfo.put("player2Name", player2 != null ? player2.getUsername() : "待定");
            if (m.getWinnerId() != null) {
                User winner = userService.getById(m.getWinnerId());
                matchInfo.put("winnerName", winner != null ? winner.getUsername() : "未知");
            }
            
            matchInfoList.add(matchInfo);
        }
        
        List<UserTournamentPoints> rankings = userTournamentPointsService.lambdaQuery()
            .eq(UserTournamentPoints::getTournamentId, id)
            .orderByDesc(UserTournamentPoints::getPoints)
            .list();
        List<Map<String, Object>> rankingInfoList = new ArrayList<>();
        for (UserTournamentPoints utp : rankings) {
            Map<String, Object> rankingInfo = new HashMap<>();
            rankingInfo.put("ranking", utp);
            
            if (utp.getUserId() == null) {
                rankingInfo.put("userName", "退赛");
                rankingInfo.put("isWithdrawn", true);
            } else {
                User user = userService.getById(utp.getUserId());
                String username = user != null ? user.getUsername() : "未知";
                rankingInfo.put("userName", username);
                rankingInfo.put("isWithdrawn", "退赛".equals(username));
            }
            
            rankingInfoList.add(rankingInfo);
        }
        
        User currentUser = getCurrentUser();
        boolean admin = isAdmin();
        boolean isHost = isHostUser(id);
        
        model.addAttribute("tournamentInfo", tournamentInfo);
        model.addAttribute("matchInfoList", matchInfoList);
        model.addAttribute("rankingInfoList", rankingInfoList);
        model.addAttribute("isAdmin", admin);
        model.addAttribute("isHost", isHost);
        model.addAttribute("currentUser", currentUser);
        
        return "tournament-detail";
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
    
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or @tournamentController.isHostUser(#tournamentId)")
    @PostMapping("/ranking/save/{tournamentId}")
    public String saveRanking(@PathVariable Long tournamentId,
                              @RequestParam String rankingData,
                              RedirectAttributes redirectAttributes) {
        try {
            // User currentUser = getCurrentUser();
            boolean admin = isAdmin();
            boolean isHost = isHostUser(tournamentId);
            
            if (!admin && !isHost) {
                redirectAttributes.addFlashAttribute("error", "您没有权限录入排名");
                return "redirect:/tournament/detail/" + tournamentId;
            }

            Tournament tournament = tournamentService.getById(tournamentId);
            if (tournament == null) {
                redirectAttributes.addFlashAttribute("error", "赛事不存在");
                return "redirect:/tournament/list";
            }
            TournamentLevel level = tournamentLevelService.lambdaQuery()
                    .eq(TournamentLevel::getCode, tournament.getLevelCode())
                    .one();

            // 解析排名数据：支持「赛事排名（级别）」粘贴格式，或原有分隔符格式
            List<String> parsedNames = parseRankingData(rankingData, tournament, level);
            if (parsedNames.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "未解析到任何选手名称，请检查输入格式");
                return "redirect:/tournament/detail/" + tournamentId;
            }

            // 删除旧的排名数据
            userTournamentPointsService.lambdaUpdate()
                    .eq(UserTournamentPoints::getTournamentId, tournamentId)
                    .remove();

            // 根据排名计算积分并保存
            int basePoints = level != null ? level.getDefaultBottomPoints() : 100;
            BigDecimal ratio = tournament.getChampionPointsRatio();
            if (ratio == null && level != null) {
                ratio = level.getDefaultChampionRatio();
            }
            
            int participantCount = parsedNames.size();
            int rank = 1;
            Set<Long> savedUserIds = new HashSet<>();
            for (String username : parsedNames) {
                String name = username == null ? "" : username.trim();
                int points = calculatePoints(rank, participantCount, basePoints, ratio);

                // 允许“退赛”出现多次：以 user_id = NULL 作为占位保存
                if ("退赛".equals(name)) {
                    UserTournamentPoints utp = new UserTournamentPoints();
                    utp.setUserId(null);
                    utp.setTournamentId(tournamentId);
                    utp.setPoints(points);
                    userTournamentPointsService.save(utp);
                    rank++;
                    continue;
                }

                User user = userService.findByUsername(name);
                if (user != null) {
                    // 同一用户重复录入时，跳过重复以避免唯一索引冲突（保留第一次出现的更高名次）
                    if (savedUserIds.add(user.getId())) {
                        UserTournamentPoints utp = new UserTournamentPoints();
                        utp.setUserId(user.getId());
                        utp.setTournamentId(tournamentId);
                        utp.setPoints(points);
                        userTournamentPointsService.save(utp);
                    }
                }
                rank++;
            }
            
            redirectAttributes.addFlashAttribute("success", "排名录入成功，共录入 " + parsedNames.size() + " 名选手");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "录入失败：" + e.getMessage());
        }
        return "redirect:/tournament/detail/" + tournamentId;
    }

    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or @tournamentController.isHostUser(#tournamentId)")
    @PostMapping("/ranking/clear/{tournamentId}")
    public String clearRanking(@PathVariable Long tournamentId, RedirectAttributes redirectAttributes) {
        try {
            boolean admin = isAdmin();
            boolean isHost = isHostUser(tournamentId);
            if (!admin && !isHost) {
                redirectAttributes.addFlashAttribute("error", "您没有权限清空排名");
                return "redirect:/tournament/detail/" + tournamentId;
            }

            userTournamentPointsService.lambdaUpdate()
                    .eq(UserTournamentPoints::getTournamentId, tournamentId)
                    .remove();

            redirectAttributes.addFlashAttribute("success", "排名已清空");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "清空失败：" + e.getMessage());
        }
        return "redirect:/tournament/detail/" + tournamentId;
    }

    /**
     * 管理员手动修改单条积分（仅超管/普管）
     */
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @PostMapping("/ranking/update/{tournamentId}/{utpId}")
    public String updateRankingPoints(@PathVariable Long tournamentId,
                                      @PathVariable Long utpId,
                                      @RequestParam Integer points,
                                      RedirectAttributes redirectAttributes) {
        try {
            if (points == null) {
                redirectAttributes.addFlashAttribute("error", "积分不能为空");
                return "redirect:/tournament/detail/" + tournamentId;
            }
            if (points < 0) {
                redirectAttributes.addFlashAttribute("error", "积分不能为负数");
                return "redirect:/tournament/detail/" + tournamentId;
            }

            UserTournamentPoints utp = userTournamentPointsService.getById(utpId);
            if (utp == null) {
                redirectAttributes.addFlashAttribute("error", "该排名记录不存在");
                return "redirect:/tournament/detail/" + tournamentId;
            }
            if (utp.getTournamentId() == null || !utp.getTournamentId().equals(tournamentId)) {
                redirectAttributes.addFlashAttribute("error", "该排名记录不属于当前赛事");
                return "redirect:/tournament/detail/" + tournamentId;
            }

            utp.setPoints(points);
            userTournamentPointsService.updateById(utp);
            redirectAttributes.addFlashAttribute("success", "积分已更新");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "修改失败：" + e.getMessage());
        }
        return "redirect:/tournament/detail/" + tournamentId;
    }
    
    /**
     * 赛事详情「复制排名」类粘贴：首行 赛事排名（级别代码），后续 序号. 选手名 - N分
     */
    private static final Pattern RANKING_TITLE_LINE = Pattern.compile("^\\s*赛事排名\\s*[（(]([^）)]+)[）)]\\s*$");
    /** 选手名与积分之间为半角或全角减号，末尾「分」可选 */
    private static final Pattern RANKING_SCORE_LINE = Pattern.compile(
            "^\\s*\\d+\\.\\s*(.+?)\\s*[-－]\\s*\\d+\\s*分?\\s*$");

    private List<String> parseRankingData(String data, Tournament tournament, TournamentLevel level) {
        if (data == null || data.trim().isEmpty()) {
            return new ArrayList<>();
        }
        List<String> structured = tryParseStructuredRankingPaste(data, tournament, level);
        if (structured != null) {
            return structured;
        }
        return parseRankingDataPlain(data);
    }

    /**
     * @return 解析到的姓名列表；若输入不是「赛事排名（…）」结构化格式则返回 null（由调用方走纯文本解析）
     */
    private List<String> tryParseStructuredRankingPaste(String data, Tournament tournament, TournamentLevel level) {
        String normalizedData = data.startsWith("\uFEFF") ? data.substring(1) : data;
        String[] rawLines = normalizedData.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        List<String> lines = new ArrayList<>();
        for (String raw : rawLines) {
            lines.add(raw == null ? "" : raw);
        }

        int i = 0;
        while (i < lines.size() && isDecorativeOrBlankRankingLine(lines.get(i))) {
            i++;
        }
        if (i >= lines.size()) {
            return null;
        }

        String first = lines.get(i).trim();
        Matcher titleMatcher = RANKING_TITLE_LINE.matcher(first);
        if (!titleMatcher.matches()) {
            return null;
        }

        String labelInTitle = titleMatcher.group(1).trim();
        if (!rankingTitleLevelMatchesTournament(labelInTitle, tournament, level)) {
            String expected = tournament.getLevelCode() != null ? tournament.getLevelCode().trim() : "（未设置）";
            throw new IllegalArgumentException(
                    "标题括号内的赛事等级「" + labelInTitle + "」与当前赛事等级「" + expected + "」不一致，已拒绝录入。");
        }

        i++;
        while (i < lines.size() && isDecorativeOrBlankRankingLine(lines.get(i))) {
            i++;
        }

        List<String> names = new ArrayList<>();
        for (; i < lines.size(); i++) {
            String line = lines.get(i);
            if (isDecorativeOrBlankRankingLine(line)) {
                continue;
            }
            String trimmed = line.trim();
            Matcher scoreMatcher = RANKING_SCORE_LINE.matcher(trimmed);
            if (scoreMatcher.matches()) {
                String name = scoreMatcher.group(1).trim();
                if (!name.isEmpty()) {
                    names.add(name);
                }
            } else {
                throw new IllegalArgumentException(
                        "无法解析的排名行（应为「序号. 选手名称 - 积分」）：" + trimmed);
            }
        }

        if (names.isEmpty()) {
            throw new IllegalArgumentException(
                    "已识别为「赛事排名（级别）」格式，但未解析到任何「序号. 选手名称 - 积分」行。");
        }
        return names;
    }

    private static boolean isDecorativeOrBlankRankingLine(String line) {
        if (line == null) {
            return true;
        }
        String t = line.trim();
        if (t.isEmpty()) {
            return true;
        }
        return t.chars().allMatch(c ->
                c == '-' || c == '—' || c == '=' || c == '_' || c == '─' || Character.isWhitespace(c));
    }

    private static boolean rankingTitleLevelMatchesTournament(String label, Tournament tournament, TournamentLevel level) {
        if (label == null || label.isEmpty()) {
            return false;
        }
        String norm = label.trim();
        String code = tournament.getLevelCode() == null ? "" : tournament.getLevelCode().trim();
        if (!code.isEmpty() && code.equalsIgnoreCase(norm)) {
            return true;
        }
        if (level != null) {
            if (level.getCode() != null && level.getCode().trim().equalsIgnoreCase(norm)) {
                return true;
            }
            if (level.getName() != null && level.getName().trim().equals(norm)) {
                return true;
            }
        }
        return false;
    }

    private List<String> parseRankingDataPlain(String data) {
        List<String> result = new ArrayList<>();
        if (data == null || data.trim().isEmpty()) {
            return result;
        }

        // 规则：
        // - 如果检测到分号/逗号/换行，则只以它们作为分隔符，忽略其它分隔符（如冒号、顿号、Tab等）
        // - 空格不再视为分隔符
        boolean hasStrongSep = data.contains("\n") || data.contains("\r")
                || data.contains(";") || data.contains("；")
                || data.contains(",") || data.contains("，");

        String normalized;
        if (hasStrongSep) {
            normalized = data
                    .replaceAll("[\\r\\n]+", ";")
                    .replaceAll("[;；]", ";")
                    .replaceAll("[,，]", ";")
                    .replaceAll(";+", ";");
        } else {
            // 仅当不存在强分隔符时，才启用其它分隔符（仍不包含空格）
            normalized = data
                    .replaceAll("[\\t]+", ";")
                    .replaceAll("[:：]", ";")
                    .replaceAll("[、]", ";")
                    .replaceAll(";+", ";");
        }

        String[] parts = normalized.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }

        return result;
    }
    
    private int calculatePoints(int rank, int participantCount, int bottomPoints, BigDecimal ratio) {
        // 定义：
        // - 冠军积分 = 参赛人数 × 冠军积分比率（比率是“每人积分系数”，不是百分比）
        // - 各名次积分在冠军与垫底之间构成等比数列（最后一名=垫底积分）
        if (ratio == null) {
            ratio = BigDecimal.ZERO;
        }
        int n = Math.max(0, participantCount);
        int championPoints = ratio.multiply(BigDecimal.valueOf(n))
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .intValue();

        if (n <= 1) {
            return Math.max(bottomPoints, championPoints);
        }

        // 若冠军积分不大于垫底积分，则所有人至少拿到底分
        if (championPoints <= bottomPoints) {
            return bottomPoints;
        }

        // 等比数列：P1=championPoints, Pn=bottomPoints
        // r = (Pn/P1)^(1/(n-1)), Pi = P1 * r^(i-1)
        double r = Math.pow((bottomPoints * 1.0) / championPoints, 1.0 / (n - 1));
        double points = championPoints * Math.pow(r, Math.max(0, rank - 1));
        int rounded = (int) Math.round(points);
        return Math.max(bottomPoints, rounded);
    }

    /**
     * 批量设置比赛局数页面
     */
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or @tournamentController.isHostUser(#tournamentId)")
    @GetMapping("/batch-set-sets/{tournamentId}")
    public String batchSetSetsPage(@PathVariable Long tournamentId, Model model, HttpServletRequest request) {
        String clientIp = IpAddressUtil.getClientIpAddress(request);
        String ipType = IpAddressUtil.classifyIpAddress(clientIp);
        model.addAttribute("clientIp", clientIp);
        model.addAttribute("ipType", ipType);

        Tournament tournament = tournamentService.getById(tournamentId);
        if (tournament == null) {
            return "redirect:/tournament/list";
        }

        boolean admin = isAdmin();
        boolean isHost = isHostUser(tournamentId);

        if (!admin && !isHost) {
            return "redirect:/tournament/detail/" + tournamentId;
        }

        List<Match> matches = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tournamentId)
                .orderByAsc(Match::getRound)
                .list();

        List<Map<String, Object>> matchInfoList = new ArrayList<>();
        for (Match m : matches) {
            Map<String, Object> matchInfo = new HashMap<>();
            matchInfo.put("match", m);

            User player1 = userService.getById(m.getPlayer1Id());
            User player2 = userService.getById(m.getPlayer2Id());
            matchInfo.put("player1Name", player1 != null ? player1.getUsername() : "待定");
            matchInfo.put("player2Name", player2 != null ? player2.getUsername() : "待定");

            // 获取当前局数
            List<SetScore> existingSets = setScoreService.lambdaQuery()
                    .eq(SetScore::getMatchId, m.getId())
                    .list();
            matchInfo.put("currentSets", existingSets.size());

            matchInfoList.add(matchInfo);
        }

        model.addAttribute("tournament", tournament);
        model.addAttribute("matchInfoList", matchInfoList);
        model.addAttribute("isAdmin", admin);
        model.addAttribute("isHost", isHost);

        return "tournament-batch-sets";
    }

    /**
     * 批量设置比赛局数
     */
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or @tournamentController.isHostUser(#tournamentId)")
    @PostMapping("/batch-set-sets/{tournamentId}")
    public String batchSetSets(@PathVariable Long tournamentId,
                               @RequestParam Integer numberOfSets,
                               @RequestParam(required = false) List<Long> matchIds,
                               RedirectAttributes redirectAttributes) {
        try {
            boolean admin = isAdmin();
            boolean isHost = isHostUser(tournamentId);

            if (!admin && !isHost) {
                redirectAttributes.addFlashAttribute("error", "您没有权限设置比赛局数");
                return "redirect:/tournament/detail/" + tournamentId;
            }

            if (numberOfSets == null || numberOfSets < 1 || numberOfSets > 20) {
                redirectAttributes.addFlashAttribute("error", "局数必须在1-20之间");
                return "redirect:/tournament/batch-set-sets/" + tournamentId;
            }

            // 获取要设置的比赛（如果未选择则设置所有比赛）
            List<Match> matches;
            if (matchIds != null && !matchIds.isEmpty()) {
                matches = matchService.lambdaQuery()
                        .eq(Match::getTournamentId, tournamentId)
                        .in(Match::getId, matchIds)
                        .list();
            } else {
                matches = matchService.lambdaQuery()
                        .eq(Match::getTournamentId, tournamentId)
                        .list();
            }

            int updatedCount = 0;
            for (Match match : matches) {
                // 删除现有局数
                setScoreService.lambdaUpdate()
                        .eq(SetScore::getMatchId, match.getId())
                        .remove();

                // 创建新的局数
                for (int i = 1; i <= numberOfSets; i++) {
                    SetScore setScore = new SetScore();
                    setScore.setMatchId(match.getId());
                    setScore.setSetNumber(i);
                    setScore.setPlayer1Score(0);
                    setScore.setPlayer2Score(0);
                    setScoreService.save(setScore);
                }
                updatedCount++;
            }

            redirectAttributes.addFlashAttribute("success",
                    "成功为 " + updatedCount + " 场比赛设置了 " + numberOfSets + " 局");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "设置失败：" + e.getMessage());
        }

        return "redirect:/tournament/detail/" + tournamentId;
    }
}