package com.example.controller;

import com.example.entity.Tournament;
import com.example.service.TournamentUrlService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 赛事详情短路径：{@code /tnd/{YYHHSS}/{levelCode}}。
 */
@Controller
public class TournamentShortPathController {

    @Autowired
    private TournamentUrlService tournamentUrlService;
    @Autowired
    private TournamentController tournamentController;

    @GetMapping("/tnd/{compact:\\d{6}}/{level}")
    public String tournamentShortDetail(@PathVariable String compact,
                                        @PathVariable String level,
                                        Model model,
                                        HttpServletRequest request) {
        Tournament tournament = tournamentUrlService.resolveCompact(compact, level);
        if (tournament == null || tournament.getId() == null) {
            return "redirect:/tournament/list";
        }
        return tournamentController.renderTournamentDetail(tournament.getId(), model, request);
    }
}
