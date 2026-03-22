package com.example.controller;

import com.example.entity.*;
import com.example.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Controller
@RequestMapping("/tournament/competition")
public class TournamentCompetitionController {

    @Autowired private ITournamentCompetitionService competitionService;
    @Autowired private UserService userService;
    @Autowired private IMatchService matchService;
    @Autowired private ISetScoreService setScoreService;

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return userService.findByUsername(auth.getName());
        }
        return null;
    }

    @PostMapping("/config/{tournamentId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or @tournamentController.isHostUser(#tournamentId)")
    public String saveConfig(@PathVariable Long tournamentId,
                             @RequestParam Integer participantCount,
                             @RequestParam Integer entryMode,
                             @RequestParam Integer matchMode,
                             @RequestParam(required = false) Integer knockoutStartRound,
                             @RequestParam(required = false) Boolean qualifierEnabled,
                             @RequestParam(required = false) Integer groupMode,
                             @RequestParam(required = false) Integer groupSize,
                             @RequestParam(required = false) Boolean groupAllowDraw,
                             @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime groupStageDeadline,
                             @RequestParam(required = false) Integer groupStageSets,
                             @RequestParam(required = false) Integer knockoutStageSets,
                             @RequestParam(required = false) Integer finalStageSets,
                             RedirectAttributes ra) {
        try {
            TournamentCompetitionConfig c = new TournamentCompetitionConfig();
            c.setTournamentId(tournamentId);
            c.setParticipantCount(participantCount);
            c.setEntryMode(entryMode);
            c.setMatchMode(matchMode);
            c.setKnockoutStartRound(knockoutStartRound);
            c.setQualifierRound(Boolean.TRUE.equals(qualifierEnabled) ? knockoutStartRound : null);
            c.setGroupMode(groupMode);
            c.setGroupSize(groupSize);
            c.setGroupAllowDraw(groupAllowDraw);
            c.setGroupStageDeadline(groupStageDeadline);
            c.setGroupStageSets(groupStageSets);
            c.setKnockoutStageSets(knockoutStageSets);
            c.setFinalStageSets(finalStageSets);
            competitionService.saveConfig(currentUser(), c);
            ra.addFlashAttribute("message", "赛事运行配置已保存");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/tournament/detail/" + tournamentId;
    }

    @PostMapping("/groups/auto-fill/{tournamentId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or @tournamentController.isHostUser(#tournamentId)")
    public String autoFillGroups(@PathVariable Long tournamentId, RedirectAttributes ra) {
        try {
            competitionService.autoFillGroupMembersFromRegistration(currentUser(), tournamentId);
            ra.addFlashAttribute("message", "已按报名接龙自动填充分组名单");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/tournament/detail/" + tournamentId;
    }

    @PostMapping("/groups/save/{tournamentId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or @tournamentController.isHostUser(#tournamentId)")
    public String saveGroups(@PathVariable Long tournamentId,
                             @RequestParam Map<String, String> rawForm,
                             RedirectAttributes ra) {
        try {
            Map<Long, List<Long>> map = new HashMap<>();
            for (Map.Entry<String, String> e : rawForm.entrySet()) {
                String key = e.getKey();
                if (!key.startsWith("group_")) continue;
                Long groupId = Long.parseLong(key.substring("group_".length()));
                String val = e.getValue();
                List<Long> users = new ArrayList<>();
                List<String> unknown = new ArrayList<>();
                List<String> names = Arrays.stream((val == null ? "" : val).split("[,，\\n\\s;；]+"))
                        .map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
                for (String name : names) {
                    User u = userService.findByUsername(name);
                    if (u == null || u.getId() == null) {
                        unknown.add(name);
                    } else {
                        users.add(u.getId());
                    }
                }
                if (!unknown.isEmpty()) {
                    throw new IllegalArgumentException("以下用户名不存在：" + String.join("、", unknown));
                }
                map.put(groupId, users);
            }
            competitionService.saveGroupMembers(currentUser(), tournamentId, map);
            ra.addFlashAttribute("message", "分组名单已保存");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/tournament/detail/" + tournamentId;
    }

    @PostMapping("/groups/generate/{tournamentId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or @tournamentController.isHostUser(#tournamentId)")
    public String generateGroupMatches(@PathVariable Long tournamentId, RedirectAttributes ra) {
        try {
            competitionService.generateGroupMatches(currentUser(), tournamentId);
            ra.addFlashAttribute("message", "小组赛对阵已自动生成");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/tournament/detail/" + tournamentId;
    }

    @PostMapping("/match/save/{matchId}")
    @ResponseBody
    public Map<String, Object> saveMatch(@PathVariable Long matchId,
                                         @RequestParam(required = false) Integer firstEndHammer,
                                         @RequestParam List<String> player1Scores,
                                         @RequestParam List<String> player2Scores,
                                         @RequestParam(required = false) Boolean autoAccept,
                                         @RequestParam(required = false) String signature) {
        try {
            competitionService.saveMatchScore(currentUser(), matchId, firstEndHammer, player1Scores, player2Scores, autoAccept, signature);
            return Map.of("ok", true);
        } catch (Exception e) {
            return Map.of("ok", false, "message", e.getMessage());
        }
    }

    @PostMapping("/match/save-super-admin/{matchId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public Map<String, Object> saveMatchBySuperAdmin(@PathVariable Long matchId,
                                                      @RequestParam(required = false) Integer firstEndHammer,
                                                      @RequestParam List<String> player1Scores,
                                                      @RequestParam List<String> player2Scores) {
        try {
            User cu = currentUser();
            competitionService.saveMatchScore(cu, matchId, firstEndHammer, player1Scores, player2Scores, true, "SUPER_ADMIN_BYPASS");
            Match m = matchService.getById(matchId);
            if (m != null) {
                m.setResultLocked(true);
                m.setAcceptedByUserId(cu != null ? cu.getId() : null);
                m.setAcceptedAt(java.time.LocalDateTime.now());
                m.setUpdatedAt(java.time.LocalDateTime.now());
                matchService.updateById(m);
            }
            return Map.of("ok", true);
        } catch (Exception e) {
            return Map.of("ok", false, "message", e.getMessage());
        }
    }

    @PostMapping("/match/accept/{matchId}")
    @ResponseBody
    public Map<String, Object> acceptMatch(@PathVariable Long matchId,
                                           @RequestParam(required = false) String signature) {
        try {
            competitionService.acceptMatchScore(currentUser(), matchId, signature);
            return Map.of("ok", true);
        } catch (Exception e) {
            return Map.of("ok", false, "message", e.getMessage());
        }
    }

    @GetMapping("/match/detail/{matchId}")
    @ResponseBody
    public Map<String, Object> matchDetail(@PathVariable Long matchId) {
        Match m = matchService.getById(matchId);
        if (m == null) return Map.of("ok", false, "message", "比赛不存在");
        List<SetScore> ss = setScoreService.lambdaQuery().eq(SetScore::getMatchId, matchId).orderByAsc(SetScore::getSetNumber).list();
        int defaultSetCount = 8;
        TournamentCompetitionConfig cfg = competitionService.getConfig(m.getTournamentId());
        if (cfg != null) {
            if ("GROUP".equalsIgnoreCase(m.getPhaseCode())) {
                defaultSetCount = (cfg.getGroupStageSets() != null && cfg.getGroupStageSets() > 0) ? cfg.getGroupStageSets() : defaultSetCount;
            } else if ("FINAL".equalsIgnoreCase(m.getPhaseCode())) {
                defaultSetCount = (cfg.getFinalStageSets() != null && cfg.getFinalStageSets() > 0) ? cfg.getFinalStageSets() : defaultSetCount;
            } else {
                defaultSetCount = (cfg.getKnockoutStageSets() != null && cfg.getKnockoutStageSets() > 0) ? cfg.getKnockoutStageSets() : defaultSetCount;
            }
        }
        List<com.example.entity.MatchAcceptance> accepts = java.util.List.of();
        List<com.example.entity.MatchScoreEditLog> logs = java.util.List.of();
        if (competitionService instanceof com.example.service.impl.TournamentCompetitionServiceImpl impl) {
            accepts = impl.listAcceptances(matchId);
            logs = impl.listScoreEditLogs(matchId);
        }
        Map<Long, String> usernameById = userService.list().stream().collect(java.util.stream.Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
        List<Map<String, Object>> acceptView = accepts.stream().map(a -> Map.<String, Object>of(
                "userId", a.getUserId(),
                "username", usernameById.getOrDefault(a.getUserId(), "未知"),
                "signature", a.getSignature(),
                "acceptedAt", a.getAcceptedAt()
        )).toList();
        List<Map<String, Object>> logView = logs.stream().map(l -> {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("setNumber", l.getSetNumber());
            row.put("editorUserId", l.getEditorUserId());
            row.put("editorUsername", usernameById.getOrDefault(l.getEditorUserId(), "未知"));
            row.put("oldPlayer1Score", l.getOldPlayer1Score());
            row.put("oldPlayer2Score", l.getOldPlayer2Score());
            row.put("newPlayer1Score", l.getNewPlayer1Score());
            row.put("newPlayer2Score", l.getNewPlayer2Score());
            row.put("oldPlayer1IsX", java.lang.Boolean.TRUE.equals(l.getOldPlayer1IsX()));
            row.put("oldPlayer2IsX", java.lang.Boolean.TRUE.equals(l.getOldPlayer2IsX()));
            row.put("newPlayer1IsX", java.lang.Boolean.TRUE.equals(l.getNewPlayer1IsX()));
            row.put("newPlayer2IsX", java.lang.Boolean.TRUE.equals(l.getNewPlayer2IsX()));
            row.put("editedAt", l.getEditedAt());
            return row;
        }).toList();
        return Map.of("ok", true, "match", m, "setScores", ss, "acceptances", acceptView, "editLogs", logView, "defaultSetCount", defaultSetCount);
    }
}
