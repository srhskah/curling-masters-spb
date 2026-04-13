package com.example.controller;

import com.example.entity.*;
import com.example.mapper.TournamentCompetitionConfigMapper;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    @Autowired private ITournamentRegistrationService tournamentRegistrationService;
    @Autowired private INotificationService notificationService;
    @Autowired private ITournamentCompetitionService tournamentCompetitionService;
    @Autowired private GroupRankingCalculator groupRankingCalculator;
    @Autowired private com.example.mapper.TournamentGroupMapper tournamentGroupMapper;
    @Autowired private com.example.mapper.TournamentGroupMemberMapper tournamentGroupMemberMapper;
    @Autowired private com.example.service.impl.DrawManagementService drawManagementService;
    @Autowired private com.example.service.TournamentDrawAuthHelper tournamentDrawAuthHelper;
    @Autowired private com.example.service.impl.TournamentRankingRosterService tournamentRankingRosterService;

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            String username = auth.getName();
            User user = userService.findByUsername(username);
            return user != null && user.getRole() <= 1;
        }
        return false;
    }

    private boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            String username = auth.getName();
            User user = userService.findByUsername(username);
            return user != null && user.getRole() != null && user.getRole() == 0;
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
                if (s != null && isTestSeries(s)) {
                    // “测试”系列不计入本赛季届次
                    continue;
                }
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
                "<strong>" + HtmlEscaper.escapeHtml(levelName) + "</strong>" +
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
            java.time.LocalDateTime nowReg = java.time.LocalDateTime.now();
            item.put("registrationOpen", tournamentRegistrationService.isRegistrationOpen(tournament, nowReg));
            item.put("registrationNavVisible", tournament.getStatus() != null && tournament.getStatus() == 0);
            
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
        actions.add(new HashMap<>(Map.of(
                "urlPrefix", "/tournament/registration/",
                "btnClass", "btn btn-sm btn-outline-success",
                "icon", "bi bi-journal-plus",
                "text", "报名接龙",
                "public", true,
                "requireRegistrationNavVisible", true
        )));
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
        if (tournamentCompetitionService instanceof com.example.service.impl.TournamentCompetitionServiceImpl impl) {
            impl.autoAcceptOverdueGroupMatches(id);
        }
        
        // User currentUser = getCurrentUser();
        boolean admin = isAdmin();
        boolean superAdmin = isSuperAdmin();
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
            Integer beforeStatus = null;
            if (tournament.getId() != null) {
                Tournament old = tournamentService.getById(tournament.getId());
                beforeStatus = old != null ? old.getStatus() : null;
            }
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
            Integer afterStatus = tournament.getStatus();
            if (!Objects.equals(beforeStatus, 1) && Objects.equals(afterStatus, 1) && tournament.getId() != null) {
                String title = "赛事状态更新：进行中";
                String markdown = "赛事 **" + (tournament.getLevelCode() == null ? "赛事" : tournament.getLevelCode())
                        + " #" + tournament.getId() + "** 已进入进行中状态。";
                notificationService.sendSystemNotification(title, markdown, "TOURNAMENT_ONGOING", tournament.getId());
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
                    .filter(s -> !isTestSeries(s))
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

        // 赛事详情页“其他赛事”区域：过滤掉“测试”系列下的所有赛事
        List<Tournament> visibleSeriesTournaments = seriesTournaments.stream()
                .filter(Objects::nonNull)
                .filter(t -> {
                    Series ts = t.getSeriesId() == null ? null : seriesById.get(t.getSeriesId());
                    return ts == null || !isTestSeries(ts);
                })
                .toList();
        if (prevSameLevelTournament != null) {
            Series ps = prevSameLevelTournament.getSeriesId() == null ? null : seriesById.get(prevSameLevelTournament.getSeriesId());
            if (ps != null && isTestSeries(ps)) {
                prevSameLevelTournament = null;
            }
        }
        if (nextSameLevelTournament != null) {
            Series ns = nextSameLevelTournament.getSeriesId() == null ? null : seriesById.get(nextSameLevelTournament.getSeriesId());
            if (ns != null && isTestSeries(ns)) {
                nextSameLevelTournament = null;
            }
        }

        // 预计算：{tournamentId -> edition}（用于 tabs 文案/箭头文案）
        Map<Long, Integer> editionByTournamentId = new HashMap<>();
        // keys: seasonId|levelCode
        Set<String> editionKeys = new HashSet<>();
        for (Tournament t : visibleSeriesTournaments) {
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
                    .filter(s -> !isTestSeries(s))
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
        for (Tournament t : visibleSeriesTournaments) {
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
        boolean hasGroupStageMatches = matches.stream()
                .anyMatch(m -> m != null && "GROUP".equalsIgnoreCase(m.getPhaseCode()));
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

        List<Long> allScoreMatchIds = matches.stream().map(Match::getId).filter(Objects::nonNull).toList();
        Map<Long, List<SetScore>> scoresByMatchId = allScoreMatchIds.isEmpty() ? new HashMap<>()
                : setScoreService.lambdaQuery().in(SetScore::getMatchId, allScoreMatchIds).list().stream()
                .collect(Collectors.groupingBy(SetScore::getMatchId, LinkedHashMap::new, Collectors.toList()));
        attachScoreCardsToMatchInfoList(matchInfoList, scoresByMatchId);
        
        List<UserTournamentPoints> rankings = tournamentRankingRosterService.filterUtpsForDisplay(id,
                userTournamentPointsService.lambdaQuery()
                        .eq(UserTournamentPoints::getTournamentId, id)
                        .orderByDesc(UserTournamentPoints::getPoints)
                        .list());
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
        boolean superAdmin = isSuperAdmin();
        boolean isHost = isHostUser(id);
        
        model.addAttribute("tournamentInfo", tournamentInfo);
        model.addAttribute("matchInfoList", matchInfoList);
        model.addAttribute("hasGroupStageMatches", hasGroupStageMatches);
        Map<Integer, List<Map<String, Object>>> matchesByRound = matchInfoList.stream()
                .collect(Collectors.groupingBy(
                        mi -> {
                            Match m = (Match) mi.get("match");
                            return m.getRound() != null ? m.getRound() : 0;
                        },
                        TreeMap::new,
                        Collectors.toList()));
        model.addAttribute("matchesByRound", matchesByRound);
        model.addAttribute("rankingInfoList", rankingInfoList);
        model.addAttribute("isAdmin", admin);
        model.addAttribute("isSuperAdmin", superAdmin);
        model.addAttribute("isHost", isHost);
        model.addAttribute("currentUser", currentUser);
        java.time.LocalDateTime nowReg = java.time.LocalDateTime.now();
        model.addAttribute("registrationOpen", tournamentRegistrationService.isRegistrationOpen(tournament, nowReg));
        model.addAttribute("registrationModuleActive", tournamentRegistrationService.registrationModuleActive(tournament, nowReg));
        model.addAttribute("registrationEnabled", tournamentRegistrationService.isRegistrationEnabled(tournament));

        // 用于：控制“比赛列表”默认显示/隐藏逻辑
        List<com.example.dto.TournamentRegistrationRowDto> regRows = tournamentRegistrationService.listRows(id, nowReg);
        boolean hasEffectiveRegistration = regRows != null
                && regRows.stream().anyMatch(com.example.dto.TournamentRegistrationRowDto::isEffectiveApproved);
        model.addAttribute("hasEffectiveRegistration", hasEffectiveRegistration);

        // 用于：赛事详情筛选（无论小组/淘汰赛 tab 均可用）
        List<User> allUsersForMatchFilter = userService.list().stream()
                .filter(u -> u.getUsername() != null && !u.getUsername().trim().isEmpty())
                .sorted(Comparator.comparing(User::getUsername, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
        model.addAttribute("allUsersForMatchFilter", allUsersForMatchFilter);

        // 筹备中/进行中：手动参赛配置 + 分组赛程（详情页展示与保存由 TournamentCompetitionService 校验状态）
        TournamentCompetitionConfig competitionConfig = tournamentCompetitionService.getConfig(id);
        model.addAttribute("competitionConfig", competitionConfig);
        model.addAttribute("competitionGroupSizeOptions",
                tournamentCompetitionService.calcGroupSizeOptions(
                        competitionConfig != null ? competitionConfig.getParticipantCount() : null));
        if (competitionConfig != null && Objects.equals(competitionConfig.getMatchMode(), 3)) {
            List<TournamentGroup> groups = tournamentGroupMapper.selectList(
                    com.baomidou.mybatisplus.core.toolkit.Wrappers.<TournamentGroup>lambdaQuery()
                            .eq(TournamentGroup::getTournamentId, id)
                            .orderByAsc(TournamentGroup::getGroupOrder));
            model.addAttribute("competitionGroups", groups);

            Map<Long, String> usernameById = userService.list().stream()
                    .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
            List<User> allUsersForGroupPick = userService.list().stream()
                    .filter(u -> u.getUsername() != null && !u.getUsername().trim().isEmpty())
                    .sorted(Comparator.comparing(User::getUsername, Comparator.nullsLast(String::compareToIgnoreCase)))
                    .toList();
            model.addAttribute("allUsersForGroupPick", allUsersForGroupPick);
            int groupSize = competitionConfig.getGroupSize() != null && competitionConfig.getGroupSize() > 0
                    ? competitionConfig.getGroupSize() : 0;
            int groupCount = (competitionConfig.getParticipantCount() != null && groupSize > 0)
                    ? (competitionConfig.getParticipantCount() / groupSize) : 0;
            List<Integer> groupMemberSlotIndexes = new ArrayList<>();
            for (int i = 0; i < groupSize; i++) {
                groupMemberSlotIndexes.add(i);
            }
            model.addAttribute("groupMemberSlotIndexes", groupMemberSlotIndexes);

            // “正赛+资格赛”下，小组下拉槽位分配规则（用于前端高亮与可选范围限制）
            TournamentRegistrationSetting regSetting = tournamentRegistrationService.getSetting(id);
            List<String> groupDirectUsernames = List.of();
            List<String> groupQualifierUsernames = List.of();
            List<String> groupEligibleUsernames = List.of();
            boolean groupPickerUseQualifierRules = false;
            boolean groupQualifierSlotDivisible = true;
            int groupQualifierSlotsPerGroup = 0;
            if (regSetting != null
                    && competitionConfig.getEntryMode() != null
                    && competitionConfig.getEntryMode() == 1
                    && groupCount > 0) {
                List<com.example.dto.TournamentRegistrationRowDto> rows = tournamentRegistrationService.listRows(id, nowReg);
                List<com.example.dto.TournamentRegistrationRowDto> eligibleRows = rows.stream()
                        .filter(com.example.dto.TournamentRegistrationRowDto::isEffectiveApproved)
                        .toList();
                if (!eligibleRows.isEmpty()) {
                    int m = regSetting.getMainDirectM() != null ? regSetting.getMainDirectM() : 0;
                    int qualifierSeedCount = regSetting.getQualifierSeedCount() != null
                            ? regSetting.getQualifierSeedCount()
                            : Math.max((regSetting.getQuotaN() != null ? regSetting.getQuotaN() : 0) - m, 0);
                    groupEligibleUsernames = eligibleRows.stream()
                            .map(com.example.dto.TournamentRegistrationRowDto::getUsername)
                            .filter(Objects::nonNull)
                            .toList();
                    int mainTake = Math.min(m, eligibleRows.size());
                    groupDirectUsernames = eligibleRows.stream()
                            .limit(mainTake)
                            .map(com.example.dto.TournamentRegistrationRowDto::getUsername)
                            .filter(Objects::nonNull)
                            .toList();
                    // 资格赛范围：已报名且未直通正赛的全部选手（不只默认种子）
                    groupQualifierUsernames = eligibleRows.stream()
                            .skip(mainTake)
                            .map(com.example.dto.TournamentRegistrationRowDto::getUsername)
                            .filter(Objects::nonNull)
                            .toList();
                    if (qualifierSeedCount > 0) {
                        groupQualifierSlotsPerGroup = (qualifierSeedCount + groupCount - 1) / groupCount; // ceil
                        groupQualifierSlotDivisible = qualifierSeedCount % groupCount == 0;
                        groupPickerUseQualifierRules = true;
                    }
                }
            }
            model.addAttribute("groupPickerUseQualifierRules", groupPickerUseQualifierRules);
            model.addAttribute("groupQualifierSlotDivisible", groupQualifierSlotDivisible);
            model.addAttribute("groupQualifierSlotsPerGroup", groupQualifierSlotsPerGroup);
            model.addAttribute("groupQualifierFlexibleSlotPerGroup",
                    groupPickerUseQualifierRules && !groupQualifierSlotDivisible && groupQualifierSlotsPerGroup > 0);
            model.addAttribute("groupDirectUsernames", groupDirectUsernames);
            model.addAttribute("groupQualifierUsernames", groupQualifierUsernames);
            model.addAttribute("groupEligibleUsernames", groupEligibleUsernames);

            Map<Long, List<Map<String, Object>>> groupMembersByGroupId = new HashMap<>();
            for (TournamentGroup g : groups) {
                List<Map<String, Object>> members = tournamentGroupMemberMapper.selectList(
                        com.baomidou.mybatisplus.core.toolkit.Wrappers.<TournamentGroupMember>lambdaQuery()
                                .eq(TournamentGroupMember::getTournamentId, id)
                                .eq(TournamentGroupMember::getGroupId, g.getId())
                                .orderByAsc(TournamentGroupMember::getSeedNo)
                                .orderByAsc(TournamentGroupMember::getId))
                        .stream().map(m -> {
                            Map<String, Object> mm = new HashMap<>();
                            mm.put("userId", m.getUserId());
                            mm.put("username", usernameById.getOrDefault(m.getUserId(), "未知"));
                            return mm;
                        }).toList();
                groupMembersByGroupId.put(g.getId(), members);
            }
            model.addAttribute("groupMembersByGroupId", groupMembersByGroupId);
            Map<Long, String> groupMemberImportTextByGroupId = new HashMap<>();
            for (TournamentGroup g : groups) {
                List<Map<String, Object>> mems = groupMembersByGroupId.get(g.getId());
                if (mems == null || mems.isEmpty()) {
                    groupMemberImportTextByGroupId.put(g.getId(), "");
                } else {
                    groupMemberImportTextByGroupId.put(g.getId(), mems.stream()
                            .map(m -> (String) m.get("username"))
                            .filter(Objects::nonNull)
                            .collect(Collectors.joining("\n")));
                }
            }
            model.addAttribute("groupMemberImportTextByGroupId", groupMemberImportTextByGroupId);

            Map<Long, String> scoreDisplayByMatchId = new HashMap<>();
            for (Match m : matches) {
                List<SetScore> ss = scoresByMatchId.getOrDefault(m.getId(), List.of());
                int p1 = ss.stream().mapToInt(x -> x.getPlayer1Score() == null ? 0 : x.getPlayer1Score()).sum();
                int p2 = ss.stream().mapToInt(x -> x.getPlayer2Score() == null ? 0 : x.getPlayer2Score()).sum();
                // 局分含 X 时仍显示双方累计总得分（与 PDF/复制文本一致）
                scoreDisplayByMatchId.put(m.getId(), ss.isEmpty() ? "-" : (p1 + " : " + p2));
            }
            model.addAttribute("scoreDisplayByMatchId", scoreDisplayByMatchId);

            Map<Long, List<Map<String, Object>>> groupMatchCards = new HashMap<>();
            for (TournamentGroup g : groups) {
                List<Map<String, Object>> cards = matches.stream()
                        .filter(m -> "GROUP".equalsIgnoreCase(m.getPhaseCode()))
                        .filter(m -> Objects.equals(m.getGroupId(), g.getId()))
                        .sorted(Comparator.comparing(Match::getRound, Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(m -> buildMatchCardForDetail(m, usernameById, scoresByMatchId, scoreDisplayByMatchId))
                        .toList();
                groupMatchCards.put(g.getId(), cards);
            }
            model.addAttribute("groupMatchCards", groupMatchCards);
            Map<Long, List<Long>> memberIdsByGroup = new HashMap<>();
            for (Map.Entry<Long, List<Map<String, Object>>> e : groupMembersByGroupId.entrySet()) {
                List<Long> uids = e.getValue().stream()
                        .map(x -> (Long) x.get("userId"))
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
                memberIdsByGroup.put(e.getKey(), uids);
            }
            Map<Long, List<Map<String, Object>>> groupRankingByGroupId = groupRankingCalculator.buildGroupRankingsByMemberIds(
                    groups, memberIdsByGroup, usernameById, matches, scoresByMatchId, competitionConfig);
            List<Match> groupMatchesOnly = matches.stream()
                    .filter(m -> "GROUP".equalsIgnoreCase(m.getPhaseCode()))
                    .toList();
            boolean allowDrawForPseudo = competitionConfig == null || !Boolean.FALSE.equals(competitionConfig.getGroupAllowDraw());
            int regularSetsForPseudo = (competitionConfig != null && competitionConfig.getGroupStageSets() != null && competitionConfig.getGroupStageSets() > 0)
                    ? competitionConfig.getGroupStageSets() : 8;
            groupRankingCalculator.buildPseudoGroupExportRowsAndApplyMainRanks(
                    groups, groupRankingByGroupId, groupMatchesOnly, scoresByMatchId, Map.of(), usernameById, allowDrawForPseudo, regularSetsForPseudo);
            model.addAttribute("groupRankingByGroupId", groupRankingByGroupId);
            List<Map<String, Object>> groupOverallRanking = groupRankingCalculator.buildOverallRanking(groupRankingByGroupId);
            Map<Long, Integer> overallRankByUserId = groupOverallRanking.stream()
                    .filter(r -> r.get("userId") != null)
                    .collect(Collectors.toMap(r -> (Long) r.get("userId"), r -> (Integer) r.get("overallRank"), (a, b) -> a));
            for (List<Map<String, Object>> oneGroup : groupRankingByGroupId.values()) {
                for (Map<String, Object> r : oneGroup) {
                    Long uid = (Long) r.get("userId");
                    r.put("overallRank", uid == null ? null : overallRankByUserId.get(uid));
                }
            }
            model.addAttribute("groupOverallRanking", groupOverallRanking);
            model.addAttribute("tournamentDrawMemberCount", drawManagementService.countGroupMembers(id));
            User drawViewer = getCurrentUser();
            model.addAttribute("canManageTournamentDraw",
                    drawViewer != null && tournamentDrawAuthHelper.canManageDraw(drawViewer, id));
        } else {
            model.addAttribute("competitionGroups", List.of());
            model.addAttribute("groupMembersByGroupId", Map.of());
            model.addAttribute("groupMemberImportTextByGroupId", Map.of());
            model.addAttribute("allUsersForGroupPick", List.of());
            model.addAttribute("groupMemberSlotIndexes", List.of());
            model.addAttribute("groupMatchCards", Map.of());
            model.addAttribute("groupRankingByGroupId", Map.of());
            model.addAttribute("groupOverallRanking", List.of());
            model.addAttribute("tournamentDrawMemberCount", null);
            model.addAttribute("canManageTournamentDraw", false);
        }

        if (!model.containsAttribute("scoreDisplayByMatchId")) {
            model.addAttribute("scoreDisplayByMatchId", buildScoreDisplayByMatchId(matches));
        }
        @SuppressWarnings("unchecked")
        Map<Long, String> scoreDisplayMap = (Map<Long, String>) model.asMap().get("scoreDisplayByMatchId");
        Map<Long, String> unameById = userService.list().stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
        TreeMap<Integer, List<Map<String, Object>>> knockoutCardsByRound = new TreeMap<>();
        TreeMap<Integer, List<Map<String, Object>>> qualifierCardsByRound = new TreeMap<>();
        for (Match m : matches) {
            String pc = m.getPhaseCode();
            if (pc != null && "GROUP".equalsIgnoreCase(pc)) {
                continue;
            }
            if (pc != null && "QUALIFIER".equalsIgnoreCase(pc)) {
                int qr = m.getQualifierRound() != null ? m.getQualifierRound() : (m.getRound() != null ? m.getRound() : 0);
                Map<String, Object> card = buildMatchCardForDetail(m, unameById, scoresByMatchId, scoreDisplayMap);
                qualifierCardsByRound.computeIfAbsent(qr, k -> new ArrayList<>()).add(card);
                continue;
            }
            int r = m.getRound() == null ? 0 : m.getRound();
            Map<String, Object> card = buildMatchCardForDetail(m, unameById, scoresByMatchId, scoreDisplayMap);
            knockoutCardsByRound.computeIfAbsent(r, k -> new ArrayList<>()).add(card);
        }
        List<Map<String, Object>> knockoutMatchCards = knockoutCardsByRound.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        model.addAttribute("knockoutMatchCards", knockoutMatchCards);
        model.addAttribute("qualifierCardsByRound", qualifierCardsByRound);
        Integer kq = competitionConfig != null ? competitionConfig.getKnockoutQualifyCount() : null;
        boolean showQualifierMatchTab = competitionConfig != null
                && Objects.equals(competitionConfig.getEntryMode(), 1)
                && kq != null && kq > 0;
        model.addAttribute("showQualifierMatchTab", showQualifierMatchTab);

        @SuppressWarnings("unchecked")
        List<TournamentGroup> groupsForMatchTabs = (List<TournamentGroup>) model.asMap().get("competitionGroups");
        if (groupsForMatchTabs == null) {
            groupsForMatchTabs = List.of();
        }
        List<Map<String, Object>> competitionMatchTabs = new ArrayList<>();
        boolean matchTabFirst = true;
        @SuppressWarnings("unchecked")
        Map<Long, List<Map<String, Object>>> groupCardsForTabs =
                (Map<Long, List<Map<String, Object>>>) model.asMap().get("groupMatchCards");
        if (groupCardsForTabs == null) {
            groupCardsForTabs = Map.of();
        }
        for (TournamentGroup g : groupsForMatchTabs) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("kind", "GROUP");
            t.put("tabId", "match-tab-group-" + g.getId());
            t.put("label", g.getGroupName());
            t.put("group", g);
            List<Map<String, Object>> oneGroupCards = groupCardsForTabs.getOrDefault(g.getId(), List.of());
            int expectedMatches = oneGroupCards.size();
            int completedNz = (int) oneGroupCards.stream()
                    .filter(c -> Boolean.TRUE.equals(c.get("completedNonZero")))
                    .count();
            t.put("expectedMatchCount", expectedMatches);
            t.put("completedMatchCount", completedNz);
            t.put("groupCompleted", expectedMatches > 0 && completedNz == expectedMatches);
            t.put("nonZeroMatchCount", completedNz);
            t.put("active", matchTabFirst);
            matchTabFirst = false;
            competitionMatchTabs.add(t);
        }
        if (showQualifierMatchTab) {
            List<Integer> qRounds = new ArrayList<>(qualifierCardsByRound.keySet());
            Collections.sort(qRounds);
            if (qRounds.isEmpty()) {
                qRounds.add(1);
            }
            int latestQ = qualifierCardsByRound.isEmpty() ? 1 : qRounds.get(qRounds.size() - 1);
            for (Integer qr : qRounds) {
                List<Map<String, Object>> qCards = qualifierCardsByRound.getOrDefault(qr, List.of());
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("kind", "QUALIFIER");
                t.put("tabId", "match-tab-qualifier-" + qr);
                t.put("label", qualifierCardsByRound.isEmpty() && qr == 1
                        ? "资格赛"
                        : ("资格赛 第" + qr + "场"));
                t.put("qualifierRound", qr);
                t.put("qualifierCards", qCards);
                t.put("qualifierLatestRound", latestQ);
                t.put("qualifierInProgress", qCards.stream().anyMatch(c -> {
                    Match mm = (Match) c.get("match");
                    return mm != null && mm.getStatus() != null && mm.getStatus() == 1;
                }));
                t.put("active", matchTabFirst);
                matchTabFirst = false;
                competitionMatchTabs.add(t);
            }
        }
        Integer configuredKoStartRound = competitionConfig != null ? competitionConfig.getKnockoutStartRound() : null;
        List<Integer> orderedKoRounds = new ArrayList<>(knockoutCardsByRound.keySet());
        orderedKoRounds.sort(Comparator.reverseOrder());
        if (configuredKoStartRound != null && orderedKoRounds.contains(configuredKoStartRound)) {
            orderedKoRounds.remove(configuredKoStartRound);
            orderedKoRounds.add(0, configuredKoStartRound);
        }
        for (Integer koRound : orderedKoRounds) {
            List<Map<String, Object>> koCards = knockoutCardsByRound.getOrDefault(koRound, List.of());
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("kind", "KO");
            t.put("tabId", "match-tab-ko-" + koRound);
            t.put("label", "淘汰赛 " + knockoutRoundLabel(koRound));
            t.put("round", koRound);
            t.put("koCards", koCards);
            t.put("active", matchTabFirst);
            matchTabFirst = false;
            competitionMatchTabs.add(t);
        }
        model.addAttribute("competitionMatchTabs", competitionMatchTabs);

        return "tournament-detail";
    }

    /**
     * 为赛事详情/批量局数等页面的 matchInfo 附加 scoreCard（与「比赛」板块小组赛/淘汰赛卡片同一套数据）。
     */
    private void attachScoreCardsToMatchInfoList(List<Map<String, Object>> matchInfoList,
            Map<Long, List<SetScore>> scoresByMatchId) {
        if (matchInfoList == null || matchInfoList.isEmpty()) {
            return;
        }
        Map<Long, String> usernameById = userService.list().stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
        Map<Long, String> scoreDisplayAll = new HashMap<>();
        for (Map<String, Object> mi : matchInfoList) {
            Match m = (Match) mi.get("match");
            if (m == null || m.getId() == null) {
                continue;
            }
            List<SetScore> ss = scoresByMatchId.getOrDefault(m.getId(), List.of());
            int p1 = ss.stream().mapToInt(x -> x.getPlayer1Score() == null ? 0 : x.getPlayer1Score()).sum();
            int p2 = ss.stream().mapToInt(x -> x.getPlayer2Score() == null ? 0 : x.getPlayer2Score()).sum();
            scoreDisplayAll.put(m.getId(), ss.isEmpty() ? "-" : (p1 + " : " + p2));
        }
        for (Map<String, Object> mi : matchInfoList) {
            Match m = (Match) mi.get("match");
            if (m == null) {
                continue;
            }
            mi.put("scoreCard", buildMatchCardForDetail(m, usernameById, scoresByMatchId, scoreDisplayAll));
        }
    }

    /**
     * 赛事详情「比赛」卡片：总比分、是否已录入（非 0:0）、胜负/平局样式用 outcome。
     */
    private Map<String, Object> buildMatchCardForDetail(Match m, Map<Long, String> usernameById,
            Map<Long, List<SetScore>> scoreByMatch, Map<Long, String> scoreDisplayMap) {
        List<SetScore> ss = scoreByMatch != null ? scoreByMatch.getOrDefault(m.getId(), List.of()) : List.of();
        int p1 = ss.stream().mapToInt(x -> x.getPlayer1Score() == null ? 0 : x.getPlayer1Score()).sum();
        int p2 = ss.stream().mapToInt(x -> x.getPlayer2Score() == null ? 0 : x.getPlayer2Score()).sum();
        boolean completedNonZero = !ss.isEmpty() && !(p1 == 0 && p2 == 0);
        String outcome;
        if (!completedNonZero) {
            outcome = "pending";
        } else if (p1 > p2) {
            outcome = "p1win";
        } else if (p2 > p1) {
            outcome = "p2win";
        } else {
            outcome = "draw";
        }
        Map<String, Object> card = new HashMap<>();
        card.put("match", m);
        card.put("player1Name", usernameById.getOrDefault(m.getPlayer1Id(), "待定"));
        card.put("player2Name", usernameById.getOrDefault(m.getPlayer2Id(), "待定"));
        String disp = scoreDisplayMap != null ? scoreDisplayMap.getOrDefault(m.getId(), "-") : (ss.isEmpty() ? "-" : (p1 + " : " + p2));
        card.put("score", disp);
        card.put("player1Total", p1);
        card.put("player2Total", p2);
        card.put("completedNonZero", completedNonZero);
        card.put("outcome", outcome);
        return card;
    }

    private Map<Long, String> buildScoreDisplayByMatchId(List<Match> matches) {
        List<Long> matchIds = matches.stream().map(Match::getId).filter(Objects::nonNull).toList();
        if (matchIds.isEmpty()) {
            return new HashMap<>();
        }
        List<SetScore> allScores = setScoreService.lambdaQuery()
                .in(SetScore::getMatchId, matchIds)
                .list();
        Map<Long, List<SetScore>> scoreByMatch = allScores.stream().collect(Collectors.groupingBy(SetScore::getMatchId));
        Map<Long, String> out = new HashMap<>();
        for (Match m : matches) {
            List<SetScore> ss = scoreByMatch.getOrDefault(m.getId(), List.of());
            int p1 = ss.stream().mapToInt(x -> x.getPlayer1Score() == null ? 0 : x.getPlayer1Score()).sum();
            int p2 = ss.stream().mapToInt(x -> x.getPlayer2Score() == null ? 0 : x.getPlayer2Score()).sum();
            out.put(m.getId(), ss.isEmpty() ? "-" : (p1 + " : " + p2));
        }
        return out;
    }

    private String knockoutRoundLabel(Integer v) {
        if (v == null) return "第 ? 轮";
        if (v == 16) return "1/16决赛";
        if (v == 8) return "1/8决赛";
        if (v == 4) return "1/4决赛";
        if (v == 2) return "半决赛";
        if (v == 1) return "奖牌赛（金/铜）";
        return "第 " + v + " 轮";
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

    private static boolean isTestSeries(Series series) {
        if (series == null) return false;
        String name = series.getName();
        return name != null && name.contains("测试");
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
            int rankingRowsSaved = 0;
            int skippedNotOnRoster = 0;
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
                    rankingRowsSaved++;
                    rank++;
                    continue;
                }

                User user = userService.findByUsername(name);
                if (user != null) {
                    // 同一用户重复录入时，跳过重复以避免唯一索引冲突（保留第一次出现的更高名次）
                    if (savedUserIds.add(user.getId())) {
                        if (tournamentRankingRosterService.shouldOmitUserFromManualRankingSave(tournamentId, user.getId())) {
                            skippedNotOnRoster++;
                            rank++;
                            continue;
                        }
                        UserTournamentPoints utp = new UserTournamentPoints();
                        utp.setUserId(user.getId());
                        utp.setTournamentId(tournamentId);
                        utp.setPoints(points);
                        userTournamentPointsService.save(utp);
                        rankingRowsSaved++;
                    }
                }
                rank++;
            }

            String okMsg = "排名录入成功，共写入 " + rankingRowsSaved + " 条积分记录（解析 " + parsedNames.size() + " 行）";
            if (skippedNotOnRoster > 0) {
                okMsg += "；已跳过 " + skippedNotOnRoster + " 名不在正赛/资格赛晋级名单中的选手";
            }
            redirectAttributes.addFlashAttribute("success", okMsg);
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

    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or @tournamentController.isHostUser(#tournamentId)")
    @PostMapping("/ranking/rebuild/{tournamentId}")
    public String rebuildRanking(@PathVariable Long tournamentId, RedirectAttributes redirectAttributes) {
        try {
            boolean admin = isAdmin();
            boolean isHost = isHostUser(tournamentId);
            if (!admin && !isHost) {
                redirectAttributes.addFlashAttribute("error", "您没有权限刷新排名");
                return "redirect:/tournament/detail/" + tournamentId;
            }
            Tournament tournament = tournamentService.getById(tournamentId);
            if (tournament == null) {
                redirectAttributes.addFlashAttribute("error", "赛事不存在");
                return "redirect:/tournament/list";
            }
            TournamentCompetitionConfig cfg = tournamentCompetitionService.getConfig(tournamentId);
            if (cfg == null || cfg.getMatchMode() == null || cfg.getMatchMode() != 3) {
                redirectAttributes.addFlashAttribute("error", "当前仅支持小组赛模式一键刷新排名");
                return "redirect:/tournament/detail/" + tournamentId;
            }
            List<Long> rankedUserIds = loadCurrentOverallRankedUserIdsForGroupStage(tournamentId, cfg);
            if (rankedUserIds.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "未找到可用于重建的实时小组赛排名数据");
                return "redirect:/tournament/detail/" + tournamentId;
            }
            TournamentLevel level = tournamentLevelService.lambdaQuery()
                    .eq(TournamentLevel::getCode, tournament.getLevelCode())
                    .one();
            int basePoints = level != null && level.getDefaultBottomPoints() != null ? level.getDefaultBottomPoints() : 100;
            BigDecimal ratio = tournament.getChampionPointsRatio();
            if (ratio == null && level != null) {
                ratio = level.getDefaultChampionRatio();
            }

            userTournamentPointsService.lambdaUpdate()
                    .eq(UserTournamentPoints::getTournamentId, tournamentId)
                    .remove();

            int participantCount = rankedUserIds.size();
            int qualifiedTotal = Math.min(participantCount,
                    Math.max(0, com.example.service.impl.KnockoutBracketService.playersInFirstKnockoutRound(cfg.getKnockoutStartRound())));
            boolean firstRoundFinished = isFirstKnockoutRoundFinished(tournamentId, cfg);

            for (int i = 0; i < rankedUserIds.size(); i++) {
                Long uid = rankedUserIds.get(i);
                if (uid == null) {
                    continue;
                }
                UserTournamentPoints utp = new UserTournamentPoints();
                utp.setUserId(uid);
                utp.setTournamentId(tournamentId);
                boolean keepBlankForQualified = !firstRoundFinished && i < qualifiedTotal;
                if (keepBlankForQualified) {
                    utp.setPoints(null);
                } else {
                    int rank = i + 1;
                    int points = calculatePoints(rank, participantCount, basePoints, ratio);
                    utp.setPoints(points);
                }
                utp.setCreatedAt(LocalDateTime.now());
                userTournamentPointsService.save(utp);
            }
            if (!firstRoundFinished && qualifiedTotal > 0) {
                redirectAttributes.addFlashAttribute("success",
                        "已按当前赛事数据重建排名（" + participantCount + " 人），首轮淘汰赛未完结：前 " + qualifiedTotal + " 名积分留空");
            } else {
                redirectAttributes.addFlashAttribute("success", "已按当前赛事数据重建排名（" + participantCount + " 人）");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "刷新排名失败：" + e.getMessage());
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

    private boolean isFirstKnockoutRoundFinished(Long tournamentId, TournamentCompetitionConfig cfg) {
        if (tournamentId == null || cfg == null || cfg.getKnockoutStartRound() == null) {
            return false;
        }
        List<Match> firstRoundMainMatches = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tournamentId)
                .eq(Match::getPhaseCode, "MAIN")
                .eq(Match::getRound, cfg.getKnockoutStartRound())
                .list();
        if (firstRoundMainMatches == null || firstRoundMainMatches.isEmpty()) {
            return false;
        }
        return firstRoundMainMatches.stream().allMatch(m -> Boolean.TRUE.equals(m.getResultLocked()));
    }

    private List<Long> loadCurrentOverallRankedUserIdsForGroupStage(Long tournamentId, TournamentCompetitionConfig cfg) {
        List<TournamentGroup> groups = tournamentGroupMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<TournamentGroup>lambdaQuery()
                        .eq(TournamentGroup::getTournamentId, tournamentId)
                        .orderByAsc(TournamentGroup::getGroupOrder)
        );
        if (groups == null || groups.isEmpty()) return List.of();
        List<TournamentGroupMember> members = tournamentGroupMemberMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<TournamentGroupMember>lambdaQuery()
                        .eq(TournamentGroupMember::getTournamentId, tournamentId)
        );
        Map<Long, List<Long>> memberIdsByGroup = members.stream().collect(Collectors.groupingBy(
                TournamentGroupMember::getGroupId, LinkedHashMap::new, Collectors.mapping(TournamentGroupMember::getUserId, Collectors.toList())
        ));
        List<Match> groupMatches = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tournamentId)
                .eq(Match::getPhaseCode, "GROUP")
                .list();
        List<Long> mids = groupMatches.stream().map(Match::getId).filter(Objects::nonNull).toList();
        Map<Long, List<SetScore>> setByMatch = mids.isEmpty() ? Map.of() : setScoreService.lambdaQuery()
                .in(SetScore::getMatchId, mids)
                .orderByAsc(SetScore::getSetNumber)
                .list()
                .stream().collect(Collectors.groupingBy(SetScore::getMatchId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, String> uname = userService.list().stream().collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
        boolean allowDraw = cfg.getGroupAllowDraw() == null || !Boolean.FALSE.equals(cfg.getGroupAllowDraw());
        int regularSets = (cfg.getGroupStageSets() != null && cfg.getGroupStageSets() > 0) ? cfg.getGroupStageSets() : 8;
        Map<Long, List<Map<String, Object>>> byGroup = groupRankingCalculator.buildGroupRankingsByMemberIds(
                groups, memberIdsByGroup, uname, groupMatches, setByMatch, allowDraw, regularSets
        );
        groupRankingCalculator.buildPseudoGroupExportRowsAndApplyMainRanks(
                groups, byGroup, groupMatches, setByMatch, Map.of(), uname, allowDraw, regularSets
        );
        List<Map<String, Object>> overall = groupRankingCalculator.buildOverallRanking(byGroup);
        List<Long> out = new ArrayList<>();
        for (Map<String, Object> row : overall) {
            Long uid = (Long) row.get("userId");
            if (uid != null) out.add(uid);
        }
        return out;
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

        List<Long> batchMids = matches.stream().map(Match::getId).filter(Objects::nonNull).toList();
        Map<Long, List<SetScore>> scoresByMatchId = batchMids.isEmpty() ? new HashMap<>()
                : setScoreService.lambdaQuery().in(SetScore::getMatchId, batchMids).list().stream()
                .collect(Collectors.groupingBy(SetScore::getMatchId, LinkedHashMap::new, Collectors.toList()));

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
        attachScoreCardsToMatchInfoList(matchInfoList, scoresByMatchId);

        model.addAttribute("tournament", tournament);
        model.addAttribute("matchInfoList", matchInfoList);
        Map<Integer, List<Map<String, Object>>> matchesByRound = matchInfoList.stream()
                .collect(Collectors.groupingBy(
                        mi -> {
                            Match mm = (Match) mi.get("match");
                            return mm != null && mm.getRound() != null ? mm.getRound() : 0;
                        },
                        java.util.TreeMap::new,
                        Collectors.toList()
                ));
        model.addAttribute("matchesByRound", matchesByRound);
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