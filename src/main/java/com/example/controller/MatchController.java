package com.example.controller;

import com.example.entity.*;
import com.example.service.*;
import com.example.util.IpAddressUtil;
import com.example.util.HtmlEscaper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.*;

@Controller
@RequestMapping("/match")
public class MatchController {

    @Autowired private IMatchService matchService;
    @Autowired private TournamentService tournamentService;
    @Autowired private SeriesService seriesService;
    @Autowired private SeasonService seasonService;
    @Autowired private ISetScoreService setScoreService;
    @Autowired private UserService userService;

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
    
    private boolean isHostUser(Long tournamentId) {
        User currentUser = getCurrentUser();
        if (currentUser == null) return false;
        Tournament tournament = tournamentService.getById(tournamentId);
        return tournament != null && tournament.getHostUserId().equals(currentUser.getId());
    }
    
    private boolean canManageMatch(Long tournamentId) {
        return isAdmin() || isHostUser(tournamentId);
    }

    @GetMapping("/list")
    public String matchList(@RequestParam(required = false) Long tournamentId,
                           Model model, HttpServletRequest request) {
        String clientIp = IpAddressUtil.getClientIpAddress(request);
        String ipType = IpAddressUtil.classifyIpAddress(clientIp);
        model.addAttribute("clientIp", clientIp);
        model.addAttribute("ipType", ipType);
        
        List<Match> matches;
        if (tournamentId != null) {
            matches = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tournamentId)
                .orderByAsc(Match::getRound)
                .list();
        } else {
            matches = matchService.list();
        }
        
        // 修复：使用Map来存储额外信息，而不是直接调用不存在的set方法
        List<Map<String, Object>> matchInfoList = new ArrayList<>();
        for (Match m : matches) {
            Map<String, Object> matchInfo = new HashMap<>();
            matchInfo.put("match", m);
            
            Tournament tournament = tournamentService.getById(m.getTournamentId());
            if (tournament != null) {
                Series series = seriesService.getById(tournament.getSeriesId());
                Season season = series != null ? seasonService.getById(series.getSeasonId()) : null;
                matchInfo.put("tournamentName", (season != null ? season.getYear() + "年" : "") + 
                    (tournament.getLevelCode() != null ? tournament.getLevelCode() : ""));
                matchInfo.put("seriesId", tournament.getSeriesId());
                matchInfo.put("isHost", isHostUser(tournament.getId()));
            }
            User player1 = userService.getById(m.getPlayer1Id());
            User player2 = userService.getById(m.getPlayer2Id());
            matchInfo.put("player1Name", player1 != null ? player1.getUsername() : "待定");
            matchInfo.put("player2Name", player2 != null ? player2.getUsername() : "待定");
            
            matchInfoList.add(matchInfo);
        }
        
        User currentUser = getCurrentUser();
        boolean admin = isAdmin();
        
        // 构建通用列表数据
        List<Map<String, Object>> dataList = new ArrayList<>();
        for (Map<String, Object> matchInfo : matchInfoList) {
            Match match = (Match) matchInfo.get("match");
            String tournamentName = (String) matchInfo.get("tournamentName");
            String player1Name = (String) matchInfo.get("player1Name");
            String player2Name = (String) matchInfo.get("player2Name");
            Boolean isHost = (Boolean) matchInfo.get("isHost");
            
            Map<String, Object> item = new HashMap<>();
            
            // 构建状态徽章
            String statusBadge = "";
            switch (match.getStatus()) {
                case 0:
                    statusBadge = "<span class=\"badge bg-secondary\">未开始</span>";
                    break;
                case 1:
                    statusBadge = "<span class=\"badge bg-primary\">进行中</span>";
                    break;
                case 2:
                    statusBadge = "<span class=\"badge bg-success\">已结束</span>";
                    break;
                case 3:
                    statusBadge = "<span class=\"badge bg-danger\">退赛</span>";
                    break;
            }
            
            // 构建比赛显示
            String matchDisplay = HtmlEscaper.escapeHtml(player1Name) + " vs " + HtmlEscaper.escapeHtml(player2Name);
            if (match.getWinnerId() != null && match.getWinnerId().equals(match.getPlayer1Id())) {
                matchDisplay += " <span class=\"text-success\">(" + HtmlEscaper.escapeHtml(player1Name) + "胜)</span>";
            } else if (match.getWinnerId() != null && match.getWinnerId().equals(match.getPlayer2Id())) {
                matchDisplay += " <span class=\"text-success\">(" + HtmlEscaper.escapeHtml(player2Name) + "胜)</span>";
            }
            
            // 构建时间显示
            String timeStr = "";
            if (match.getScheduledTime() != null) {
                timeStr = match.getScheduledTime().toLocalDate() + " " + match.getScheduledTime().toLocalTime();
            } else {
                timeStr = "-";
            }
            
            // 构建创建时间显示
            String createTimeStr = match.getCreateTime() != null ? 
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(match.getCreateTime()) : "-";
            
            item.put("data", Arrays.asList(
                match.getId(),
                "<strong>" + HtmlEscaper.escapeHtml(tournamentName) + "</strong>",
                match.getCategory() != null ? match.getCategory() : "-",
                match.getRound() != null ? "第" + match.getRound() + "轮" : "-",
                matchDisplay,
                statusBadge,
                timeStr,
                createTimeStr
            ));
            item.put("filters", Map.of("status", match.getStatus()));
            item.put("id", match.getId());
            
            dataList.add(item);
        }
        
        // 构建列配置
        List<Map<String, Object>> columns = new ArrayList<>();
        columns.add(Map.of("title", "ID", "type", "text"));
        columns.add(Map.of("title", "赛事", "type", "custom"));
        columns.add(Map.of("title", "类别", "type", "text"));
        columns.add(Map.of("title", "轮次", "type", "text"));
        columns.add(Map.of("title", "对阵", "type", "custom"));
        columns.add(Map.of("title", "状态", "type", "custom"));
        columns.add(Map.of("title", "预定时间", "type", "text"));
        columns.add(Map.of("title", "创建时间", "type", "text"));
        
        // 构建操作按钮
        List<Map<String, Object>> actions = new ArrayList<>();
        actions.add(Map.of("urlPrefix", "/match/detail/", "btnClass", "btn btn-sm btn-outline-info", "icon", "bi bi-eye", "text", "详情"));
        actions.add(Map.of("urlPrefix", "/match/edit/", "btnClass", "btn btn-sm btn-outline-warning", "icon", "bi bi-pencil", "text", "编辑"));
        actions.add(Map.of("urlPrefix", "/match/score/", "btnClass", "btn btn-sm btn-outline-success", "icon", "bi bi-pencil-square", "text", "录入比分"));
        
        // 通用列表参数
        model.addAttribute("pageTitle", "比赛列表");
        model.addAttribute("pageIcon", "bi bi-controller");
        model.addAttribute("entityName", "比赛");
        model.addAttribute("addUrl", "/match/add" + (tournamentId != null ? "?tournamentId=" + tournamentId : ""));
        model.addAttribute("dataList", dataList);
        model.addAttribute("columns", columns);
        model.addAttribute("actions", actions);
        model.addAttribute("hasActions", true);
        model.addAttribute("emptyIcon", "bi bi-controller");
        model.addAttribute("emptyMessage", "暂无比赛数据");
        model.addAttribute("tournamentId", tournamentId);
        model.addAttribute("isAdmin", admin);
        model.addAttribute("currentUser", currentUser);
        
        return "generic-list";
    }
    
    @GetMapping("/add")
    public String addMatchPage(@RequestParam Long tournamentId,
                              @RequestParam(required = false) Long seriesId,
                              Model model, HttpServletRequest request) {
        String clientIp = IpAddressUtil.getClientIpAddress(request);
        String ipType = IpAddressUtil.classifyIpAddress(clientIp);
        model.addAttribute("clientIp", clientIp);
        model.addAttribute("ipType", ipType);
        
        if (!canManageMatch(tournamentId)) {
            return "redirect:/tournament/list";
        }
        
        Tournament tournament = tournamentService.getById(tournamentId);
        if (tournament == null) {
            return "redirect:/tournament/list";
        }
        
        List<User> allUsers = userService.list();
        
        // 构建用户选项
        List<Map<String, Object>> userOptions = new ArrayList<>();
        for (User user : allUsers) {
            userOptions.add(Map.of(
                "value", user.getId(),
                "text", user.getUsername()
            ));
        }
        
        // 构建表单字段配置
        List<Map<String, Object>> fields = new ArrayList<>();
        
        fields.add(Map.of(
            "type", "hidden",
            "id", "tournamentId",
            "name", "tournamentId",
            "value", tournamentId.toString()
        ));
        
        fields.add(Map.of(
            "type", "text",
            "id", "category",
            "name", "category",
            "label", "比赛类别",
            "placeholder", "如：1000赛资格赛、1/8决赛等",
            "required", true,
            "help", "比赛的类别描述"
        ));
        
        fields.add(Map.of(
            "type", "number",
            "id", "round",
            "name", "round",
            "label", "轮次",
            "placeholder", "如：1、2、3等",
            "min", "1",
            "required", true,
            "help", "比赛轮次，用于排序"
        ));
        
        fields.add(Map.of(
            "type", "select",
            "id", "player1Id",
            "name", "player1Id",
            "label", "选手1",
            "placeholder", "请选择选手1",
            "required", true,
            "help", "选择第一位选手",
            "options", userOptions
        ));
        
        fields.add(Map.of(
            "type", "select",
            "id", "player2Id",
            "name", "player2Id",
            "label", "选手2",
            "placeholder", "请选择选手2",
            "required", true,
            "help", "选择第二位选手",
            "options", userOptions
        ));
        
        fields.add(Map.of(
            "type", "datetime-local",
            "id", "scheduledTime",
            "name", "scheduledTime",
            "label", "预定时间",
            "help", "比赛预定开始时间（可选）"
        ));
        
        fields.add(Map.of(
            "type", "select",
            "id", "firstEndHammer",
            "name", "firstEndHammer",
            "label", "先手后手",
            "placeholder", "请选择",
            "help", "第一局谁先手",
            "options", Arrays.asList(
                Map.of("value", "1", "text", "选手1先手"),
                Map.of("value", "2", "text", "选手2先手")
            )
        ));
        
        // 通用表单参数
        model.addAttribute("pageTitle", "添加比赛");
        model.addAttribute("pageIcon", "bi bi-controller");
        model.addAttribute("saveUrl", "/match/save");
        model.addAttribute("backUrl", "/match/list?tournamentId=" + tournamentId);
        model.addAttribute("formData", new Match());
        model.addAttribute("fields", fields);
        model.addAttribute("clientIp", clientIp);
        model.addAttribute("ipType", ipType);
        
        return "generic-form";
    }
    
    @GetMapping("/edit/{id}")
    public String editMatchPage(@PathVariable Long id, Model model, HttpServletRequest request) {
        String clientIp = IpAddressUtil.getClientIpAddress(request);
        String ipType = IpAddressUtil.classifyIpAddress(clientIp);
        
        Match match = matchService.getById(id);
        if (match == null) {
            return "redirect:/match/list";
        }
        
        if (!canManageMatch(match.getTournamentId())) {
            return "redirect:/match/list?tournamentId=" + match.getTournamentId();
        }
        
        List<User> allUsers = userService.list();
        
        // 构建用户选项
        List<Map<String, Object>> userOptions = new ArrayList<>();
        for (User user : allUsers) {
            userOptions.add(Map.of(
                "value", user.getId(),
                "text", user.getUsername()
            ));
        }
        
        // 构建表单字段配置
        List<Map<String, Object>> fields = new ArrayList<>();
        
        fields.add(Map.of(
            "type", "hidden",
            "id", "tournamentId",
            "name", "tournamentId",
            "value", match.getTournamentId().toString()
        ));
        
        fields.add(Map.of(
            "type", "text",
            "id", "category",
            "name", "category",
            "label", "比赛类别",
            "placeholder", "如：1000赛资格赛、1/8决赛等",
            "required", true,
            "help", "比赛的类别描述"
        ));
        
        fields.add(Map.of(
            "type", "number",
            "id", "round",
            "name", "round",
            "label", "轮次",
            "placeholder", "如：1、2、3等",
            "min", "1",
            "required", true,
            "help", "比赛轮次，用于排序"
        ));
        
        fields.add(Map.of(
            "type", "select",
            "id", "player1Id",
            "name", "player1Id",
            "label", "选手1",
            "placeholder", "请选择选手1",
            "required", true,
            "help", "选择第一位选手",
            "options", userOptions
        ));
        
        fields.add(Map.of(
            "type", "select",
            "id", "player2Id",
            "name", "player2Id",
            "label", "选手2",
            "placeholder", "请选择选手2",
            "required", true,
            "help", "选择第二位选手",
            "options", userOptions
        ));
        
        fields.add(Map.of(
            "type", "datetime-local",
            "id", "scheduledTime",
            "name", "scheduledTime",
            "label", "预定时间",
            "help", "比赛预定开始时间（可选）"
        ));
        
        fields.add(Map.of(
            "type", "select",
            "id", "firstEndHammer",
            "name", "firstEndHammer",
            "label", "先手后手",
            "placeholder", "请选择",
            "help", "第一局谁先手",
            "options", Arrays.asList(
                Map.of("value", "1", "text", "选手1先手"),
                Map.of("value", "2", "text", "选手2先手")
            )
        ));
        
        // 通用表单参数
        model.addAttribute("pageTitle", "编辑比赛");
        model.addAttribute("pageIcon", "bi bi-controller");
        model.addAttribute("saveUrl", "/match/save");
        model.addAttribute("backUrl", "/match/list?tournamentId=" + match.getTournamentId());
        model.addAttribute("formData", match);
        model.addAttribute("fields", fields);
        model.addAttribute("clientIp", clientIp);
        model.addAttribute("ipType", ipType);
        
        return "generic-form";
    }
    
    @PostMapping("/save")
    public String saveMatch(@ModelAttribute Match match,
                           @RequestParam(required = false) Long seriesId,
                           RedirectAttributes redirectAttributes) {
        try {
            if (!canManageMatch(match.getTournamentId())) {
                redirectAttributes.addFlashAttribute("error", "您没有权限管理此比赛");
                return "redirect:/match/list";
            }
            
            if (match.getPlayer1Id() != null && match.getPlayer2Id() != null && 
                match.getPlayer1Id().equals(match.getPlayer2Id())) {
                redirectAttributes.addFlashAttribute("error", "两名选手不能相同");
                return "redirect:/match/add?tournamentId=" + match.getTournamentId();
            }
            
            if (match.getId() == null) {
                match.setStatus((byte) 0);
                match.setCreatedAt(LocalDateTime.now());
                matchService.save(match);
                redirectAttributes.addFlashAttribute("success", "比赛创建成功");
            } else {
                match.setUpdatedAt(LocalDateTime.now());
                matchService.updateById(match);
                redirectAttributes.addFlashAttribute("success", "比赛更新成功");
            }
            
            String redirectUrl = seriesId != null ? "/match/list?seriesId=" + seriesId + "&tournamentId=" + match.getTournamentId() : "/match/list?tournamentId=" + match.getTournamentId();
            return "redirect:" + redirectUrl;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "操作失败：" + e.getMessage());
            return match.getId() == null ? "redirect:/match/add?tournamentId=" + match.getTournamentId() : "redirect:/match/edit/" + match.getId();
        }
    }
    
    @GetMapping("/score/{id}")
    public String matchScorePage(@PathVariable Long id,
                                 @RequestParam(required = false) Long seriesId,
                                 Model model, HttpServletRequest request) {
        String clientIp = IpAddressUtil.getClientIpAddress(request);
        String ipType = IpAddressUtil.classifyIpAddress(clientIp);
        model.addAttribute("clientIp", clientIp);
        model.addAttribute("ipType", ipType);
        
        Match match = matchService.getById(id);
        if (match == null) {
            return "redirect:/match/list";
        }
        
        if (!canManageMatch(match.getTournamentId())) {
            return "redirect:/match/list?tournamentId=" + match.getTournamentId();
        }
        
        Tournament tournament = tournamentService.getById(match.getTournamentId());
        User player1 = userService.getById(match.getPlayer1Id());
        User player2 = userService.getById(match.getPlayer2Id());
        
        List<SetScore> setScores = setScoreService.lambdaQuery()
            .eq(SetScore::getMatchId, id)
            .orderByAsc(SetScore::getSetNumber)
            .list();
        
        int totalPlayer1 = 0, totalPlayer2 = 0;
        for (SetScore ss : setScores) {
            totalPlayer1 += ss.getPlayer1Score();
            totalPlayer2 += ss.getPlayer2Score();
        }
        
        model.addAttribute("match", match);
        model.addAttribute("tournament", tournament);
        model.addAttribute("player1", player1);
        model.addAttribute("player2", player2);
        model.addAttribute("setScores", setScores);
        model.addAttribute("totalPlayer1", totalPlayer1);
        model.addAttribute("totalPlayer2", totalPlayer2);
        model.addAttribute("seriesId", seriesId);
        
        return "match-score";
    }
    
    @PostMapping("/score/save/{matchId}")
    public String saveMatchScore(@PathVariable Long matchId,
                                @RequestParam(required = false) Boolean simpleMode,
                                @RequestParam(required = false) List<Integer> setNumbers,
                                @RequestParam(required = false) List<Integer> player1Scores,
                                @RequestParam(required = false) List<Integer> player2Scores,
                                @RequestParam(required = false) Integer player1TotalScore,
                                @RequestParam(required = false) Integer player2TotalScore,
                                @RequestParam(required = false) Long seriesId,
                                RedirectAttributes redirectAttributes) {
        try {
            Match match = matchService.getById(matchId);
            if (match == null) {
                redirectAttributes.addFlashAttribute("error", "比赛不存在");
                return "redirect:/match/list";
            }
            
            if (!canManageMatch(match.getTournamentId())) {
                redirectAttributes.addFlashAttribute("error", "您没有权限录入比分");
                return "redirect:/match/list?tournamentId=" + match.getTournamentId();
            }
            
            // 删除旧比分
            setScoreService.lambdaUpdate()
                .eq(SetScore::getMatchId, matchId)
                .remove();
            
            if (simpleMode != null && simpleMode) {
                // 简单模式：只录一局总分
                SetScore ss = new SetScore();
                ss.setMatchId(matchId);
                ss.setSetNumber(1);
                ss.setPlayer1Score(player1TotalScore != null ? player1TotalScore : 0);
                ss.setPlayer2Score(player2TotalScore != null ? player2TotalScore : 0);
                ss.setCreatedAt(LocalDateTime.now());
                setScoreService.save(ss);
                
                // 更新胜者
                if (player1TotalScore != null && player2TotalScore != null) {
                    if (player1TotalScore > player2TotalScore) {
                        match.setWinnerId(match.getPlayer1Id());
                    } else if (player2TotalScore > player1TotalScore) {
                        match.setWinnerId(match.getPlayer2Id());
                    }
                    match.setStatus((byte) 2);
                    matchService.updateById(match);
                }
            } else if (setNumbers != null && !setNumbers.isEmpty()) {
                // 详细模式：录每一局
                int totalPlayer1 = 0, totalPlayer2 = 0;
                for (int i = 0; i < setNumbers.size(); i++) {
                    SetScore ss = new SetScore();
                    ss.setMatchId(matchId);
                    ss.setSetNumber(setNumbers.get(i));
                    ss.setPlayer1Score(player1Scores != null && i < player1Scores.size() ? player1Scores.get(i) : 0);
                    ss.setPlayer2Score(player2Scores != null && i < player2Scores.size() ? player2Scores.get(i) : 0);
                    ss.setCreatedAt(LocalDateTime.now());
                    setScoreService.save(ss);
                    
                    totalPlayer1 += ss.getPlayer1Score();
                    totalPlayer2 += ss.getPlayer2Score();
                }
                
                // 更新胜者
                if (totalPlayer1 > totalPlayer2) {
                    match.setWinnerId(match.getPlayer1Id());
                } else if (totalPlayer2 > totalPlayer1) {
                    match.setWinnerId(match.getPlayer2Id());
                }
                match.setStatus((byte) 2);
                matchService.updateById(match);
            }
            
            redirectAttributes.addFlashAttribute("success", "比分保存成功");
            
            String redirectUrl = seriesId != null ? "/match/score/" + matchId + "?seriesId=" + seriesId : "/match/score/" + matchId;
            return "redirect:" + redirectUrl;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "保存失败：" + e.getMessage());
            return "redirect:/match/score/" + matchId;
        }
    }
}