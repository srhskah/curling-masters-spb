package com.example.controller;

import com.example.dto.h2h.H2hDebugStats;
import com.example.dto.h2h.H2hPayload;
import com.example.dto.h2h.H2hUserOption;
import com.example.entity.*;
import com.example.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.FORBIDDEN;

/**
 * 交手记录（H2H）查询：页面与 JSON API。
 */
@Controller
public class HeadToHeadController {

    @Autowired private HeadToHeadQueryService headToHeadQueryService;
    @Autowired private IMatchService matchService;
    @Autowired private TournamentService tournamentService;
    @Autowired private UserService userService;
    @Autowired private ISetScoreService setScoreService;
    @Autowired private ITournamentCompetitionService tournamentCompetitionService;

    @GetMapping("/h2h")
    public String h2hPage(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            model.addAttribute("currentUser", userService.findByUsername(auth.getName()));
        }
        return "h2h";
    }

    @GetMapping(value = "/h2h/api/users", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<H2hUserOption> apiUsers() {
        return headToHeadQueryService.listAllPlayersForPicker();
    }

    @GetMapping(value = "/h2h/api/opponents", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<H2hUserOption> apiOpponents(@RequestParam("userId") Long userId) {
        return headToHeadQueryService.listOpponentsOf(userId);
    }

    /**
     * userId2 可选：仅传 userId1 时返回该选手参与的全部历史场次；两者皆传则为双方交手场次。
     */
    @GetMapping(value = "/h2h/api/matches", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public H2hPayload apiMatches(@RequestParam("userId1") Long userId1,
                                 @RequestParam(required = false) Long userId2) {
        return headToHeadQueryService.buildPayload(userId1, userId2);
    }

    /**
     * 超级管理员专用：返回 H2H 查询各阶段计数，便于对照数据库与管理页列表。
     */
    @GetMapping(value = "/h2h/api/debug/matches", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public H2hDebugStats apiDebugMatches(@RequestParam("userId1") Long userId1,
                                         @RequestParam(required = false) Long userId2) {
        if (!isSuperAdmin()) {
            throw new ResponseStatusException(FORBIDDEN, "仅超级管理员可访问该调试接口");
        }
        return headToHeadQueryService.debugMatchStats(userId1, userId2);
    }

    /**
     * iframe 内嵌的比赛详情：公开可读；仅超级管理员可编辑比分（与赛事详情页 iframe 一致，但主办/管理员在此为只读）。
     */
    @GetMapping("/h2h/match/{id}")
    public String h2hMatchFrame(@PathVariable Long id,
                                Model model) {
        Match match = matchService.getById(id);
        if (match == null) {
            return "redirect:/h2h";
        }

        Tournament tournament = tournamentService.getById(match.getTournamentId());
        User player1 = match.getPlayer1Id() != null ? userService.getById(match.getPlayer1Id()) : null;
        User player2 = match.getPlayer2Id() != null ? userService.getById(match.getPlayer2Id()) : null;

        List<SetScore> setScores = setScoreService.lambdaQuery()
                .eq(SetScore::getMatchId, id)
                .orderByAsc(SetScore::getSetNumber)
                .list();

        int totalPlayer1 = 0;
        int totalPlayer2 = 0;
        for (SetScore ss : setScores) {
            totalPlayer1 += ss.getPlayer1Score() != null ? ss.getPlayer1Score() : 0;
            totalPlayer2 += ss.getPlayer2Score() != null ? ss.getPlayer2Score() : 0;
        }

        boolean resultLocked = Boolean.TRUE.equals(match.getResultLocked());
        boolean superAdmin = isSuperAdmin();
        boolean canSubmitScore = superAdmin && !resultLocked;

        int defaultSetCount = 8;
        TournamentCompetitionConfig cfg = tournamentCompetitionService.getConfig(match.getTournamentId());
        if (cfg != null) {
            if ("GROUP".equalsIgnoreCase(match.getPhaseCode())) {
                defaultSetCount = (cfg.getGroupStageSets() != null && cfg.getGroupStageSets() > 0) ? cfg.getGroupStageSets() : defaultSetCount;
            } else if ("FINAL".equalsIgnoreCase(match.getPhaseCode())) {
                defaultSetCount = (cfg.getFinalStageSets() != null && cfg.getFinalStageSets() > 0) ? cfg.getFinalStageSets() : defaultSetCount;
            } else {
                defaultSetCount = (cfg.getKnockoutStageSets() != null && cfg.getKnockoutStageSets() > 0) ? cfg.getKnockoutStageSets() : defaultSetCount;
            }
        }

        model.addAttribute("match", match);
        model.addAttribute("tournament", tournament);
        model.addAttribute("player1", player1);
        model.addAttribute("player2", player2);
        model.addAttribute("setScores", setScores);
        model.addAttribute("totalPlayer1", totalPlayer1);
        model.addAttribute("totalPlayer2", totalPlayer2);
        model.addAttribute("seriesId", null);
        model.addAttribute("resultLocked", resultLocked);
        model.addAttribute("canSubmitScore", canSubmitScore);
        model.addAttribute("isSuperAdmin", superAdmin);
        model.addAttribute("defaultSetCount", defaultSetCount);
        model.addAttribute("h2hFrame", true);
        model.addAttribute("backUrl", "/h2h");

        return "h2h-match-frame";
    }

    private boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            User user = userService.findByUsername(auth.getName());
            return user != null && user.getRole() != null && user.getRole() == 0;
        }
        return false;
    }
}
