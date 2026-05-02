package com.example.controller;

import com.example.entity.*;
import com.example.service.*;
import com.example.util.MatchPhaseClassifier;
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
    @Autowired private com.example.service.impl.QualifierScheduleService qualifierScheduleService;
    @Autowired private com.example.service.impl.KnockoutBracketService knockoutBracketService;
    @Autowired private TournamentService tournamentService;

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
                             @RequestParam(defaultValue = "false") boolean qualifierEnabled,
                             @RequestParam(required = false) Integer groupMode,
                             @RequestParam(required = false) Integer groupSize,
                             @RequestParam(required = false) Boolean groupAllowDraw,
                             @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime groupStageDeadline,
                             @RequestParam(required = false) Integer groupStageSets,
                             @RequestParam(required = false) Integer knockoutStageSets,
                             @RequestParam(required = false) Integer finalStageSets,
                             @RequestParam(required = false) Integer knockoutQualifyCount,
                             @RequestParam(required = false) Integer qualifierSets,
                             @RequestParam(required = false, defaultValue = "0") Integer knockoutBracketMode,
                             @RequestParam(required = false, defaultValue = "true") String knockoutAutoFromGroup,
                             RedirectAttributes ra) {
        try {
            TournamentCompetitionConfig c = new TournamentCompetitionConfig();
            c.setTournamentId(tournamentId);
            c.setParticipantCount(participantCount);
            c.setEntryMode(entryMode);
            c.setMatchMode(matchMode);
            c.setKnockoutStartRound(knockoutStartRound);
            c.setQualifierRound(qualifierEnabled ? knockoutStartRound : null);
            c.setKnockoutQualifyCount(knockoutQualifyCount);
            c.setQualifierSets(qualifierSets);
            if (knockoutBracketMode != null && (knockoutBracketMode < 0 || knockoutBracketMode > 2)) {
                throw new IllegalArgumentException("淘汰赛对阵模式须为 0～2");
            }
            c.setKnockoutBracketMode(knockoutBracketMode != null ? knockoutBracketMode : 0);
            c.setKnockoutAutoFromGroup(!"false".equalsIgnoreCase(knockoutAutoFromGroup));
            c.setGroupMode(groupMode);
            c.setGroupSize(groupSize);
            c.setGroupAllowDraw(groupAllowDraw);
            c.setGroupStageDeadline(groupStageDeadline);
            c.setGroupStageSets(groupStageSets);
            c.setKnockoutStageSets(knockoutStageSets);
            c.setFinalStageSets(finalStageSets);
            TournamentCompetitionConfig saved = competitionService.saveConfig(currentUser(), c);
            ra.addFlashAttribute("message",
                    String.format("赛事运行配置已保存（模式=%s，淘汰赛首轮=%s，资格赛挂载=%s）",
                            saved.getMatchMode(),
                            saved.getKnockoutStartRound() == null ? "未设置" : saved.getKnockoutStartRound(),
                            saved.getQualifierRound() == null ? "关闭" : ("开启@"+saved.getQualifierRound())));
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/tournament/detail/" + tournamentId;
    }

    @PostMapping("/knockout/generate/{tournamentId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or @tournamentController.isHostUser(#tournamentId)")
    public String generateKnockout(@PathVariable Long tournamentId, RedirectAttributes ra) {
        try {
            int n = knockoutBracketService.generateFirstKnockoutRound(currentUser(), tournamentId);
            ra.addFlashAttribute("message", "已生成淘汰赛首轮，共 " + n + " 场");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/tournament/detail/" + tournamentId;
    }

    @PostMapping("/knockout/generate-next/{tournamentId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or @tournamentController.isHostUser(#tournamentId)")
    public String generateNextKnockout(@PathVariable Long tournamentId, RedirectAttributes ra) {
        try {
            int n = knockoutBracketService.generateNextKnockoutRound(currentUser(), tournamentId);
            ra.addFlashAttribute("message", "已按当前赛果手动生成下一轮，共新增 " + n + " 场");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/tournament/detail/" + tournamentId;
    }

    @PostMapping("/knockout/clear/{tournamentId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or @tournamentController.isHostUser(#tournamentId)")
    public String clearKnockout(@PathVariable Long tournamentId, RedirectAttributes ra) {
        try {
            knockoutBracketService.deleteAllKnockoutMatches(currentUser(), tournamentId);
            ra.addFlashAttribute("message", "已清空本赛事淘汰赛（MAIN/FINAL）场次");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/tournament/detail/" + tournamentId;
    }

    @PostMapping("/knockout/recompute-acceptance/{tournamentId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or @tournamentController.isHostUser(#tournamentId)")
    public String recomputeKnockoutAcceptance(@PathVariable Long tournamentId, RedirectAttributes ra) {
        try {
            int n = competitionService.recomputeKnockoutAcceptanceStates(currentUser(), tournamentId);
            ra.addFlashAttribute("message", "已按当前规则回补淘汰赛验收状态，新增“已验收” " + n + " 场");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/tournament/detail/" + tournamentId;
    }

    @GetMapping("/knockout/manual/{tournamentId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or @tournamentController.isHostUser(#tournamentId)")
    public String knockoutManualPage(@PathVariable Long tournamentId, org.springframework.ui.Model model, RedirectAttributes ra) {
        try {
            User cu = currentUser();
            List<Long> eligible = knockoutBracketService.loadEligibleFirstRoundPlayers(cu, tournamentId);
            List<com.example.service.impl.KnockoutBracketService.ManualPairDraft> draft =
                    knockoutBracketService.buildManualFirstRoundDraft(cu, tournamentId);
            Map<Long, User> usersById = userService.listByIds(eligible).stream()
                    .collect(java.util.stream.Collectors.toMap(User::getId, u -> u, (a, b) -> a));
            List<Map<String, Object>> options = new ArrayList<>();
            int rank = 1;
            for (Long uid : eligible) {
                User u = usersById.get(uid);
                if (u == null) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("rank", rank);
                row.put("userId", uid);
                row.put("username", u.getUsername());
                options.add(row);
                rank++;
            }
            model.addAttribute("tournamentId", tournamentId);
            model.addAttribute("tournamentName", "赛事#" + tournamentId);
            model.addAttribute("koManualOptions", options);
            model.addAttribute("koManualDraft", draft);
            return "tournament/knockout-manual-first-round";
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/tournament/detail/" + tournamentId;
        }
    }

    @GetMapping("/knockout/manual-data/{tournamentId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or @tournamentController.isHostUser(#tournamentId)")
    @ResponseBody
    public Map<String, Object> knockoutManualData(@PathVariable Long tournamentId) {
        try {
            User cu = currentUser();
            List<Long> eligible = knockoutBracketService.loadEligibleFirstRoundPlayers(cu, tournamentId);
            List<com.example.service.impl.KnockoutBracketService.ManualPairDraft> draft =
                    knockoutBracketService.buildManualFirstRoundDraft(cu, tournamentId);
            Map<Long, User> usersById = userService.listByIds(eligible).stream()
                    .collect(java.util.stream.Collectors.toMap(User::getId, u -> u, (a, b) -> a));
            List<Map<String, Object>> options = new ArrayList<>();
            int rank = 1;
            for (Long uid : eligible) {
                User u = usersById.get(uid);
                if (u == null) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("rank", rank);
                row.put("userId", uid);
                row.put("username", u.getUsername());
                options.add(row);
                rank++;
            }
            List<Map<String, Object>> draftRows = new ArrayList<>();
            for (com.example.service.impl.KnockoutBracketService.ManualPairDraft p : draft) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("slot", p.slot);
                row.put("label", p.label);
                row.put("defaultPlayer1Id", p.defaultPlayer1Id);
                row.put("defaultPlayer2Id", p.defaultPlayer2Id);
                draftRows.add(row);
            }
            return Map.of("ok", true, "options", options, "draft", draftRows);
        } catch (Exception e) {
            return Map.of("ok", false, "message", e.getMessage());
        }
    }

    @PostMapping("/knockout/manual/{tournamentId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or @tournamentController.isHostUser(#tournamentId)")
    public String generateKnockoutManual(@PathVariable Long tournamentId,
                                         @RequestParam Map<String, String> rawForm,
                                         RedirectAttributes ra) {
        try {
            int pairCount = Integer.parseInt(rawForm.getOrDefault("pairCount", "0"));
            List<com.example.service.impl.KnockoutBracketService.ManualPairInput> pairs = new ArrayList<>();
            for (int i = 0; i < pairCount; i++) {
                String p1s = rawForm.get("p1_" + i);
                String p2s = rawForm.get("p2_" + i);
                Long p1 = (p1s == null || p1s.isBlank()) ? null : Long.parseLong(p1s);
                Long p2 = (p2s == null || p2s.isBlank()) ? null : Long.parseLong(p2s);
                pairs.add(new com.example.service.impl.KnockoutBracketService.ManualPairInput(p1, p2));
            }
            int n = knockoutBracketService.generateFirstKnockoutRoundManual(currentUser(), tournamentId, pairs);
            ra.addFlashAttribute("message", "已按手动排签生成淘汰赛首轮，共 " + n + " 场");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/tournament/competition/knockout/manual/" + tournamentId;
        }
        return "redirect:/tournament/detail/" + tournamentId;
    }

    @PostMapping("/qualifier/generate-first-round/{tournamentId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or @tournamentController.isHostUser(#tournamentId)")
    public String generateQualifierFirstRound(@PathVariable Long tournamentId, RedirectAttributes ra) {
        try {
            int n = qualifierScheduleService.generateQualifierFirstRound(currentUser(), tournamentId);
            ra.addFlashAttribute("message", n > 0 ? ("已生成资格赛首轮 " + n + " 场") : "当前资格赛人数不超过名额，无需生成对阵");
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
                com.example.dto.GroupImportResult parsed = userService.resolveGroupImport(val);
                if (!parsed.getUnknownUsernames().isEmpty()) {
                    throw new IllegalArgumentException("以下用户名不存在：" + String.join("、", parsed.getUnknownUsernames()));
                }
                map.put(groupId, parsed.getUserIds());
            }
            competitionService.saveGroupMembers(currentUser(), tournamentId, map);
            ra.addFlashAttribute("message", "分组名单已保存");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/tournament/detail/" + tournamentId;
    }

    @PostMapping("/groups/save-group/{tournamentId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or @tournamentController.isHostUser(#tournamentId)")
    public String saveGroupMembersOne(@PathVariable Long tournamentId,
                                      @RequestParam Long groupId,
                                      @RequestParam(value = "groupMembers", required = false) String groupMembers,
                                      RedirectAttributes ra) {
        try {
            String raw = groupMembers == null ? "" : groupMembers;
            com.example.dto.GroupImportResult parsed = userService.resolveGroupImport(raw);
            if (!parsed.getUnknownUsernames().isEmpty()) {
                throw new IllegalArgumentException("以下用户名不存在：" + String.join("、", parsed.getUnknownUsernames()));
            }
            competitionService.saveGroupMembersForGroup(currentUser(), tournamentId, groupId, parsed.getUserIds());
            ra.addFlashAttribute("message", "已保存该小组名单");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/tournament/detail/" + tournamentId;
    }

    @PostMapping("/groups/sync-matches/{tournamentId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or @tournamentController.isHostUser(#tournamentId)")
    public String syncGroupMatchesOne(@PathVariable Long tournamentId,
                                      @RequestParam Long groupId,
                                      RedirectAttributes ra) {
        try {
            competitionService.syncGroupMatchesForGroup(currentUser(), tournamentId, groupId);
            ra.addFlashAttribute("message", "已按当前名单同步该组小组赛对阵（仅增删本组需调整的场次）");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/tournament/detail/" + tournamentId;
    }

    @PostMapping("/groups/save-all-and-generate/{tournamentId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or @tournamentController.isHostUser(#tournamentId)")
    public String saveAllGroupsAndGenerate(@PathVariable Long tournamentId,
                                           @RequestParam Map<String, String> rawForm,
                                           RedirectAttributes ra) {
        try {
            Map<Long, String> raw = new HashMap<>();
            for (Map.Entry<String, String> e : rawForm.entrySet()) {
                String key = e.getKey();
                if (!key.startsWith("group_")) continue;
                Long groupId = Long.parseLong(key.substring("group_".length()));
                raw.put(groupId, e.getValue() != null ? e.getValue() : "");
            }
            competitionService.saveAllGroupsRosterAndGenerateMatches(currentUser(), tournamentId, raw);
            ra.addFlashAttribute("message", "已保存当前各组名单并全量重新生成小组赛对阵");
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
            competitionService.saveMatchScore(currentUser(), matchId, firstEndHammer, player1Scores, player2Scores, autoAccept, signature, false);
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
            competitionService.saveMatchScore(cu, matchId, firstEndHammer, player1Scores, player2Scores, true, "SUPER_ADMIN_BYPASS", false);
            Match m = matchService.getById(matchId);
            if (m != null) {
                m.setResultLocked(true);
                m.setAcceptedByUserId(cu != null ? cu.getId() : null);
                m.setAcceptedAt(java.time.LocalDateTime.now());
                m.setUpdatedAt(java.time.LocalDateTime.now());
                matchService.updateById(m);
                Match fresh = matchService.getById(matchId);
                if (fresh != null) {
                    knockoutBracketService.tryAutoGenerateFromGroupStage(cu, fresh.getTournamentId());
                    knockoutBracketService.onKnockoutMatchLocked(cu, fresh);
                }
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

    @PostMapping("/group/disqualify/{tournamentId}/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or @tournamentController.isHostUser(#tournamentId)")
    @ResponseBody
    public Map<String, Object> submitGroupDisqualification(@PathVariable Long tournamentId,
                                                           @PathVariable Long userId,
                                                           @RequestParam(required = false) String reason,
                                                           @RequestParam(required = false) String signature) {
        try {
            Map<String, Object> data = competitionService.submitGroupDisqualification(currentUser(), tournamentId, userId, reason, signature);
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();
            out.put("ok", true);
            out.putAll(data);
            return out;
        } catch (Exception e) {
            return Map.of("ok", false, "message", e.getMessage());
        }
    }

    @GetMapping("/group/disqualify/list/{tournamentId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or @tournamentController.isHostUser(#tournamentId)")
    @ResponseBody
    public Map<String, Object> listGroupDisqualifications(@PathVariable Long tournamentId) {
        try {
            return Map.of("ok", true, "rows", competitionService.listGroupDisqualifications(tournamentId));
        } catch (Exception e) {
            return Map.of("ok", false, "message", e.getMessage(), "rows", List.of());
        }
    }

    @PostMapping("/match/save-and-accept/{matchId}")
    @ResponseBody
    public Map<String, Object> saveAndAcceptMatch(@PathVariable Long matchId,
                                                  @RequestParam(required = false) Integer firstEndHammer,
                                                  @RequestParam List<String> player1Scores,
                                                  @RequestParam List<String> player2Scores,
                                                  @RequestParam(required = false) String signature) {
        try {
            competitionService.saveMatchScoreThenAccept(currentUser(), matchId, firstEndHammer, player1Scores, player2Scores, signature);
            return Map.of("ok", true);
        } catch (Exception e) {
            return Map.of("ok", false, "message", e.getMessage());
        }
    }

    @GetMapping("/match/detail/{matchId}")
    @ResponseBody
    public Map<String, Object> matchDetail(@PathVariable Long matchId) {
        User viewer = currentUser();
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
        boolean canEdit = viewer != null && competitionService.canEditMatchScore(viewer, m);
        boolean canAccept = viewer != null && competitionService.canAcceptMatchScore(viewer, m);
        boolean viewerIsSuperAdmin = viewer != null && viewer.getRole() != null && viewer.getRole() == 0;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("match", m);
        // 显式构造局分 JSON，保证 player1IsX 等为布尔、避免仅实体序列化时字段缺失导致前端刷新后无法还原 X/先后手
        body.put("setScores", ss.stream().map(TournamentCompetitionController::toSetScoreView).toList());
        body.put("matchFirstEndHammer", m.getFirstEndHammer() == null ? null : m.getFirstEndHammer().intValue());
        body.put("matchPhaseKind", MatchPhaseClassifier.classify(m));
        body.put("acceptances", acceptView);
        body.put("editLogs", logView);
        body.put("defaultSetCount", defaultSetCount);
        body.put("canEditScore", canEdit);
        body.put("canAcceptScore", canAccept);
        body.put("viewerIsSuperAdmin", viewerIsSuperAdmin);
        return body;
    }

    private static Map<String, Object> toSetScoreView(SetScore s) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", s.getId());
        row.put("setNumber", s.getSetNumber());
        row.put("player1Score", s.getPlayer1Score());
        row.put("player2Score", s.getPlayer2Score());
        row.put("player1IsX", Boolean.TRUE.equals(s.getPlayer1IsX()));
        row.put("player2IsX", Boolean.TRUE.equals(s.getPlayer2IsX()));
        row.put("hammerPlayerId", s.getHammerPlayerId());
        row.put("isBlankEnd", s.getIsBlankEnd());
        return row;
    }
}
