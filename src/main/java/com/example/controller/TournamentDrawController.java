package com.example.controller;

import com.example.dto.DrawPool;
import com.example.entity.*;
import com.example.mapper.*;
import com.example.service.*;
import com.example.service.impl.DrawManagementService;
import com.example.service.impl.RankingExportPdfService;
import com.example.util.PdfExportSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.text.Collator;
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
    @Autowired private RankingExportPdfService rankingExportPdfService;
    @Autowired private RankingApiController rankingApiController;

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
    @Transactional(rollbackFor = Exception.class)
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
            if (tournamentId == null || tournamentId <= 0) {
                return Map.of("success", false, "message", "赛事编号无效，请从赛事详情重新进入抽签页");
            }
            Tournament tournament = tournamentService.getById(tournamentId);
            if (tournament == null || tournament.getId() == null) {
                return Map.of("success", false, "message", "赛事不存在或编号无效，请从赛事详情重新进入抽签页");
            }
            Long tid = tournament.getId();
            if (groupCount == null || groupCount < 1) {
                return Map.of("success", false, "message", "小组数无效");
            }
            if (p == DrawPool.MAIN) {
                List<TournamentGroup> existingGroups = groupMapper.selectList(
                        com.baomidou.mybatisplus.core.toolkit.Wrappers.<TournamentGroup>lambdaQuery()
                                .eq(TournamentGroup::getTournamentId, tid));
                if (existingGroups.size() != groupCount) {
                    drawManagementService.syncMainDrawGroupSkeleton(tid, groupCount);
                }
            }
            TournamentDraw draw = drawManagementService.initializeDraw(tid, drawType, groupCount, tierCount, seedCount, p);
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
                                           @RequestParam(required = false) Long groupId,
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

    /**
     * 抽签结果 PDF（标题含赛季-级别-本届届次；仅办赛权限）
     */
    @GetMapping(value = "/export/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportDrawResultsPdf(@PathVariable Long tournamentId,
                                                       @RequestParam(required = false, defaultValue = "MAIN") String pool,
                                                       Authentication auth) {
        User user = userService.getById(((CustomUserDetails) auth.getPrincipal()).getId());
        if (!tournamentDrawAuthHelper.canManageDraw(user, tournamentId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        DrawPool p = DrawPool.fromParam(pool);
        Map<String, Object> status = drawManagementService.getDrawStatus(tournamentId, p);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resultRows = (List<Map<String, Object>>) status.getOrDefault("resultRows", List.of());

        Collator collator = Collator.getInstance(Locale.CHINA);
        Map<String, List<Map<String, Object>>> byName = new TreeMap<>(collator);
        for (Map<String, Object> row : resultRows) {
            Object gn = row.get("groupName");
            String key = gn != null ? String.valueOf(gn) : "?";
            byName.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }
        for (List<Map<String, Object>> list : byName.values()) {
            list.sort(Comparator.comparing(
                    r -> {
                        Object o = r.get("drawOrder");
                        return o instanceof Number ? ((Number) o).intValue() : null;
                    },
                    Comparator.nullsLast(Integer::compareTo)));
        }
        List<Map<String, Object>> groupSections = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> e : byName.entrySet()) {
            LinkedHashMap<String, Object> sec = new LinkedHashMap<>();
            sec.put("groupName", e.getKey());
            sec.put("rows", e.getValue());
            groupSections.add(sec);
        }

        String editionTitle = buildDrawPdfEditionTitle(tournamentId);
        String poolSuffix = p == DrawPool.QUALIFIER ? "资格赛" : "正赛";
        String title = editionTitle + "-" + poolSuffix + "-抽签结果";

        TournamentDraw draw = (TournamentDraw) status.get("draw");
        LinkedHashMap<String, Object> model = new LinkedHashMap<>();
        PdfExportSupport.addStandardPdfHeaderFields(model);
        model.put("title", title);
        model.put("drawTypeLabel", drawTypeLabelForPdf(draw));
        model.put("groupSections", groupSections);
        model.put("noDrawRecords", resultRows.isEmpty());

        byte[] pdfBytes = rankingExportPdfService.renderPdf("pdf/pdf-tournament-draw-results", model);
        return PdfExportSupport.attachmentPdf(pdfBytes, editionTitle + "-" + poolSuffix + "-抽签结果.pdf");
    }

    private String buildDrawPdfEditionTitle(Long tournamentId) {
        Map<String, Object> data = rankingApiController.getTournamentRanking(tournamentId);
        String seasonLabel = data.get("seasonLabel") != null ? data.get("seasonLabel").toString() : "赛季";
        String levelName = data.get("levelName") != null ? data.get("levelName").toString() : "赛事等级";
        Integer edition = null;
        try {
            edition = data.get("edition") instanceof Number ? ((Number) data.get("edition")).intValue() : null;
        } catch (Exception ignored) {
        }
        return seasonLabel + "-" + levelName + "-" + (edition == null ? "?" : edition);
    }

    private static String drawTypeLabelForPdf(TournamentDraw draw) {
        if (draw == null || draw.getDrawType() == null) {
            return "";
        }
        return switch (draw.getDrawType()) {
            case "RANDOM" -> "默认抽签";
            case "TIERED" -> "分档抽签";
            case "SEED" -> "种子抽签";
            default -> draw.getDrawType();
        };
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

