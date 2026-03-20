package com.example.controller;

import com.example.dto.RankingEntry;
import com.example.entity.Season;
import com.example.service.RankingService;
import com.example.service.SeasonService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/ranking")
public class RankingController {

    @Autowired private RankingService rankingService;
    @Autowired private SeasonService seasonService;

    @GetMapping
    public String rankingHome(Model model, HttpServletRequest request) {
        List<RankingEntry> total = rankingService.getTotalRanking(null);

        List<Season> seasons = seasonService.lambdaQuery()
                .orderByDesc(Season::getYear)
                .orderByDesc(Season::getHalf)
                .list();

        Season currentSeason = seasons.isEmpty() ? null : seasons.get(0);
        List<RankingEntry> currentSeasonRankingTop24 = currentSeason == null
                ? List.of()
                : rankingService.getSeasonRanking(currentSeason.getId(), 24);

        // 各赛季排名：每个赛季仅取前24（用于选项卡）
        Map<Long, List<RankingEntry>> seasonTop24 = new LinkedHashMap<>();
        for (Season s : seasons) {
            seasonTop24.put(s.getId(), rankingService.getSeasonRanking(s.getId(), 24));
        }

        model.addAttribute("pageTitle", "排名");
        model.addAttribute("pageIcon", "bi bi-list-ol");
        model.addAttribute("totalRanking", total);
        model.addAttribute("seasons", seasons);
        model.addAttribute("currentSeason", currentSeason);
        model.addAttribute("currentSeasonRankingTop24", currentSeasonRankingTop24);
        model.addAttribute("seasonTop24", seasonTop24);
        return "ranking";
    }

    @GetMapping("/season/{seasonId}")
    public String seasonRanking(@PathVariable Long seasonId, Model model) {
        Season season = seasonService.getById(seasonId);
        if (season == null) {
            return "redirect:/ranking";
        }
        model.addAttribute("pageTitle", "赛季排名");
        model.addAttribute("pageIcon", "bi bi-calendar3");
        model.addAttribute("season", season);
        model.addAttribute("seasonRanking", rankingService.getSeasonRanking(seasonId, null));
        return "ranking-season";
    }
}

