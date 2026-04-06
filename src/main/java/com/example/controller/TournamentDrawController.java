package com.example.controller;

import com.example.dto.DrawPool;
import com.example.entity.*;
import com.example.mapper.*;
import com.example.service.*;
import com.example.service.impl.DrawManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/tournament/{tournamentId}/draw")
public class TournamentDrawController {

    @Autowired private TournamentService tournamentService;
    @Autowired private TournamentGroupMapper groupMapper;
    @Autowired private DrawManagementService drawManagementService;
    @Autowired private UserService userService;
    @Autowired private SeasonService seasonService;
    @Autowired private SeriesService seriesService;
    @Autowired private ITournamentLevelService tournamentLevelService;
    @Autowired private TournamentDrawAuthHelper tournamentDrawAuthHelper;

    /**
     * 获取抽签配置（用于对话框）
     */
    @GetMapping("/config")
    @ResponseBody
    public Map<String, Object> getDrawConfig(@PathVariable Long tournamentId,
            @RequestParam(required = false) Integer groupCount,
            @RequestParam(required = false, defaultValue = "MAIN") String pool) {
        DrawPool p = DrawPool.fromParam(pool);
        Map<String, Object> config = drawManagementService.getDrawConfig(tournamentId, p);
        
        // 获取有效的小组数选项
        @SuppressWarnings("unchecked")
        List<Long> drawParticipants = (List<Long>) config.get("drawParticipants");
        int totalPlayers = drawParticipants != null ? drawParticipants.size() : 0;
        
        List<Integer> validGroupCounts = totalPlayers > 0 ?
            drawManagementService.getValidGroupCounts(totalPlayers) : new ArrayList<>();

        config.put("validGroupCounts", validGroupCounts);
        Object recGc = config.get("recommendedGroupCount");
        int gcForTier = groupCount != null && groupCount > 0 ? groupCount
                : (recGc instanceof Number ? ((Number) recGc).intValue() : 0);
        if (gcForTier > 1 && totalPlayers > 0 && totalPlayers % gcForTier == 0) {
            int ppg = totalPlayers / gcForTier;
            config.put("validTieredTierCounts",
                    drawManagementService.getValidTieredDrawTierCounts(gcForTier, ppg));
            config.put("validSeedCountOptions",
                    drawManagementService.getValidSeedCountOptions(gcForTier, ppg, totalPlayers));
        } else {
            config.put("validTieredTierCounts", List.of());
            config.put("validSeedCountOptions", List.of());
        }

        return config;
    }

    /**
     * 抽签页面
     */
    @GetMapping
    public String drawPage(@PathVariable Long tournamentId, Model model, Authentication auth,
            HttpServletRequest request) {
        Tournament tournament = tournamentService.getById(tournamentId);
        if (tournament == null) {
            return "redirect:/";
        }
        
        if (tournament.getStatus() == null || (tournament.getStatus() != 0 && tournament.getStatus() != 1)) {
            return "redirect:/tournament/detail/" + tournamentId + "?error=draw_status";
        }
        DrawPool drawPool = DrawPool.fromParam(request.getParameter("pool"));
        if (drawManagementService.countGroupMembers(tournamentId) > 0
                && !drawManagementService.allowDrawPageWhenGroupMembersPresent(tournamentId)) {
            return "redirect:/tournament/detail/" + tournamentId + "?error=draw_group_saved";
        }

        CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();
        Long currentUserId = userDetails.getId();
        User currentUser = userService.getById(currentUserId);
        boolean canManageDraw = tournamentDrawAuthHelper.canManageDraw(currentUser, tournamentId);

        // 获取抽签配置信息（包括报名名单和赛事配置）
        Map<String, Object> drawConfig = drawManagementService.getDrawConfig(tournamentId, drawPool);
        
        @SuppressWarnings("unchecked")
        List<Long> drawParticipants = (List<Long>) drawConfig.get("drawParticipants");
        boolean isRegistered = drawParticipants != null && drawParticipants.contains(currentUserId);
        int totalPlayers = drawParticipants != null ? drawParticipants.size() : 0;

        // 获取抽签状态
        Map<String, Object> drawStatus = drawManagementService.getDrawStatus(tournamentId, drawPool);
        TournamentDraw draw = (TournamentDraw) drawStatus.get("draw");
        LocalDateTime now = LocalDateTime.now();
        boolean drawWindowOpen = draw != null && drawManagementService.isDrawWindowOpen(draw, now);

        // 获取小组
        List<TournamentGroup> groups = groupMapper.selectList(
            com.baomidou.mybatisplus.core.toolkit.Wrappers.<TournamentGroup>lambdaQuery()
                .eq(TournamentGroup::getTournamentId, tournamentId)
                .orderByAsc(TournamentGroup::getGroupOrder)
        );

        // 获取有效的小组数和档数选项
        List<Integer> validGroupCounts = totalPlayers > 0 ? drawManagementService.getValidGroupCounts(totalPlayers) : new ArrayList<>();
        List<Integer> validTieredTierCounts = new ArrayList<>();
        List<Integer> validSeedCountOptions = new ArrayList<>();
        if (draw != null && draw.getGroupCount() != null && totalPlayers > 0 && totalPlayers % draw.getGroupCount() == 0) {
            int playersPerGroup = totalPlayers / draw.getGroupCount();
            validTieredTierCounts = drawManagementService.getValidTieredDrawTierCounts(draw.getGroupCount(), playersPerGroup);
            validSeedCountOptions = drawManagementService.getValidSeedCountOptions(draw.getGroupCount(), playersPerGroup, totalPlayers);
        }

        Set<Long> eligibleGroupIdSet = new HashSet<>(drawManagementService.getEligibleGroupIds(tournamentId, currentUserId, drawPool));

        com.example.entity.TournamentRegistrationSetting regSetting =
                (com.example.entity.TournamentRegistrationSetting) drawConfig.get("registrationSetting");
        String registrationDeadlineText = "-";
        if (regSetting != null && regSetting.getDeadline() != null) {
            registrationDeadlineText = regSetting.getDeadline().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        }

        Map<String, Object> drawUserHints = new LinkedHashMap<>();
        Integer myTier = drawPool == DrawPool.MAIN
                ? drawManagementService.getParticipantTierNumber(tournamentId, currentUserId) : null;
        if (myTier != null) {
            drawUserHints.put("tierNumber", myTier);
        }
        if (draw != null && "SEED".equals(draw.getDrawType()) && drawPool == DrawPool.MAIN) {
            boolean seed = drawManagementService.isUserSeedParticipant(tournamentId, currentUserId);
            drawUserHints.put("isSeed", seed);
            drawUserHints.put("seedLabel", seed ? "种子选手" : "非种子选手");
        }

        model.addAttribute("tournament", tournament);
        model.addAttribute("isRegistered", isRegistered);
        model.addAttribute("totalPlayers", totalPlayers);
        model.addAttribute("draw", draw);
        model.addAttribute("groups", groups);
        model.addAttribute("validGroupCounts", validGroupCounts);
        model.addAttribute("validTieredTierCounts", validTieredTierCounts);
        model.addAttribute("validTierCounts", validTieredTierCounts);
        model.addAttribute("validSeedCountOptions", validSeedCountOptions);
        model.addAttribute("drawStatus", drawStatus);
        model.addAttribute("drawConfig", drawConfig);
        model.addAttribute("canManageDraw", canManageDraw);
        model.addAttribute("drawWindowOpen", drawWindowOpen);
        model.addAttribute("eligibleGroupIdSet", eligibleGroupIdSet);
        model.addAttribute("registrationDeadlineText", registrationDeadlineText);
        model.addAttribute("drawUserHints", drawUserHints);
        model.addAttribute("drawPool", drawPool.name());
        model.addAttribute("allowQualifierDrawPage", drawManagementService.allowDrawPageWhenGroupMembersPresent(tournamentId));
        model.addAttribute("qualifierPipelineComplete", drawManagementService.isQualifierDrawPipelineComplete(tournamentId));
        model.addAttribute("pageTitle", tournament.getLevelCode() != null ? tournament.getLevelCode() + " - 抽签" : "赛事抽签");
        model.addAttribute("clientIp", com.example.util.IpAddressUtil.getClientIpAddress(request));
        model.addAttribute("ipType", com.example.util.IpAddressUtil.classifyIpAddress(com.example.util.IpAddressUtil.getClientIpAddress(request)));

        return "tournament/draw";
    }

    /**
     * 初始化抽签配置（管理员）
     */
    @PostMapping("/init")
    @ResponseBody
    public Map<String, Object> initDraw(@PathVariable Long tournamentId,
                                        @RequestParam String drawType,
                                        @RequestParam Integer groupCount,
                                        @RequestParam(required = false) Integer tierCount,
                                        @RequestParam(required = false) Integer seedCount,
                                        @RequestParam(required = false, defaultValue = "MAIN") String pool,
                                        Authentication auth) {
        try {
            DrawPool p = DrawPool.fromParam(pool);
            User user = userService.getById(((CustomUserDetails) auth.getPrincipal()).getId());
            if (!tournamentDrawAuthHelper.canManageDraw(user, tournamentId)) {
                return Map.of("success", false, "message", "仅超级管理员、管理员或本届主办可配置抽签");
            }
            // 创建小组
            List<TournamentGroup> existingGroups = groupMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<TournamentGroup>lambdaQuery()
                    .eq(TournamentGroup::getTournamentId, tournamentId)
            );
            
            if (p == DrawPool.MAIN && existingGroups.size() != groupCount) {
                // 删除旧小组
                groupMapper.delete(
                    com.baomidou.mybatisplus.core.toolkit.Wrappers.<TournamentGroup>lambdaQuery()
                        .eq(TournamentGroup::getTournamentId, tournamentId)
                );
                
                // 创建新小组
                String[] groupNames = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L"};
                for (int i = 0; i < groupCount; i++) {
                    TournamentGroup group = new TournamentGroup();
                    group.setTournamentId(tournamentId);
                    group.setGroupName(groupNames[i] + "组");
                    group.setGroupOrder(i + 1);
                    groupMapper.insert(group);
                }
            }
            
            TournamentDraw draw = drawManagementService.initializeDraw(tournamentId, drawType, groupCount, tierCount, seedCount, p);
            return Map.of("success", true, "draw", draw);
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /**
     * 执行抽签
     */
    @PostMapping("/perform")
    @ResponseBody
    public Map<String, Object> performDraw(@PathVariable Long tournamentId,
                                           @RequestParam Long groupId,
                                           @RequestParam(required = false, defaultValue = "MAIN") String pool,
                                           Authentication auth) {
        try {
            CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();
            Long userId = userDetails.getId();
            DrawPool p = DrawPool.fromParam(pool);
            TournamentDrawResult result = drawManagementService.performDraw(tournamentId, userId, groupId, p);
            return Map.of("success", true, "result", result);
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /**
     * 获取抽签结果
     */
    @GetMapping("/results")
    @ResponseBody
    public Map<String, Object> getDrawResults(@PathVariable Long tournamentId,
                                              @RequestParam(required = false, defaultValue = "MAIN") String pool) {
        Tournament tournament = tournamentService.getById(tournamentId);
        Map<String, Object> drawStatus = drawManagementService.getDrawStatus(tournamentId, DrawPool.fromParam(pool));
        if (tournament == null) {
            return Map.of("error", "赛事不存在");
        }
        
        List<TournamentGroup> groups = groupMapper.selectList(
            com.baomidou.mybatisplus.core.toolkit.Wrappers.<TournamentGroup>lambdaQuery()
                .eq(TournamentGroup::getTournamentId, tournamentId)
                .orderByAsc(TournamentGroup::getGroupOrder)
        );
        
        // 获取赛事信息
        String seasonLabel = "";
        String seriesLabel = "";
        String levelLabel = "";
        Integer edition = null;
        
        if (tournament.getSeriesId() != null) {
            Series series = seriesService.getById(tournament.getSeriesId());
            if (series != null && series.getSeasonId() != null) {
                Season season = seasonService.getById(series.getSeasonId());
                if (season != null) {
                    seasonLabel = season.getYear() + "年" + (season.getHalf() == 1 ? "上半年" : "下半年");
                    
                    // 计算届次
                    List<Long> seasonSeriesIds = seriesService.lambdaQuery()
                        .eq(Series::getSeasonId, season.getId())
                        .list().stream()
                            .filter(s -> !isTestSeries(s))
                            .map(Series::getId)
                            .collect(Collectors.toList());
                    
                    if (!seasonSeriesIds.isEmpty() && tournament.getLevelCode() != null) {
                        List<Tournament> sameLevel = tournamentService.lambdaQuery()
                            .in(Tournament::getSeriesId, seasonSeriesIds)
                            .eq(Tournament::getLevelCode, tournament.getLevelCode())
                            .orderByAsc(Tournament::getCreatedAt)
                            .list();
                        
                        for (int i = 0; i < sameLevel.size(); i++) {
                            if (sameLevel.get(i).getId().equals(tournamentId)) {
                                edition = i + 1;
                                break;
                            }
                        }
                    }
                }
                seriesLabel = "第" + series.getSequence() + "系列";
            }
        }
        
        if (tournament.getLevelCode() != null) {
            TournamentLevel level = tournamentLevelService.lambdaQuery()
                .eq(TournamentLevel::getCode, tournament.getLevelCode())
                .one();
            if (level != null) {
                levelLabel = level.getName();
            }
        }
        
        Map<String, Object> result = new HashMap<>(drawStatus);
        result.put("groups", groups);
        result.put("seasonLabel", seasonLabel);
        result.put("seriesLabel", seriesLabel);
        result.put("levelLabel", levelLabel);
        result.put("edition", edition);
        
        return result;
    }

    private static boolean isTestSeries(Series series) {
        if (series == null) return false;
        String name = series.getName();
        return name != null && name.contains("测试");
    }
    
    /**
     * 导入抽签结果到小组成员表（管理员）
     */
    @PostMapping("/import-to-groups")
    @ResponseBody
    public Map<String, Object> importToGroups(@PathVariable Long tournamentId,
                                              @RequestParam(required = false, defaultValue = "MAIN") String pool,
                                              Authentication auth) {
        try {
            User user = userService.getById(((CustomUserDetails) auth.getPrincipal()).getId());
            if (!tournamentDrawAuthHelper.canManageDraw(user, tournamentId)) {
                return Map.of("success", false, "message", "仅超级管理员、管理员或本届主办可导入小组");
            }
            int n = drawManagementService.importDrawResultsToGroups(tournamentId, DrawPool.fromParam(pool));
            return Map.of("success", true, "message", "成功导入 " + n + " 名选手到小组，赛事已进入进行中");
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }
}

