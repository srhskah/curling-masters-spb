package com.example.controller;

import com.example.dto.RankingLeaderboardEntry;
import com.example.service.LeaderboardService;
import com.example.service.SeasonService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 排行榜控制器
 */
@Controller
@RequestMapping("/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;
    private final SeasonService seasonService;

    /**
     * 排行榜页面
     */
    @GetMapping
    public String leaderboardPage(Model model) {
        // 默认显示历史数据（seasonId = null）
        model.addAttribute("currentSeasonId", null);
        model.addAttribute("seasons", seasonService.list());
        return "leaderboard";
    }

    /**
     * API：获取排行榜数据
     */
    @GetMapping("/api")
    @ResponseBody
    public Map<String, Object> getLeaderboardData(
            @RequestParam(required = false) Long seasonId,
            @RequestParam(defaultValue = "10") Integer limit) {
        
        System.out.println("=== 排行榜 API 调用 ===");
        System.out.println("seasonId: " + seasonId);
        System.out.println("limit: " + limit);
        
        Map<String, Object> result = new HashMap<>();
        
        List<RankingLeaderboardEntry> winRate = leaderboardService.getWinRateLeaderboard(seasonId, limit);
        List<RankingLeaderboardEntry> highestSetScore = leaderboardService.getHighestSetScoreLeaderboard(seasonId, limit);
        List<RankingLeaderboardEntry> stealCount = leaderboardService.getStealCountLeaderboard(seasonId, limit);
        List<RankingLeaderboardEntry> highestStealScore = leaderboardService.getHighestStealScoreLeaderboard(seasonId, limit);
        
        System.out.println("winRate count: " + winRate.size());
        System.out.println("highestSetScore count: " + highestSetScore.size());
        System.out.println("stealCount count: " + stealCount.size());
        System.out.println("highestStealScore count: " + highestStealScore.size());
        
        result.put("winRate", winRate);
        result.put("highestSetScore", highestSetScore);
        result.put("stealCount", stealCount);
        result.put("highestStealScore", highestStealScore);
        result.put("seasonId", seasonId);
        
        // 添加当前用户在各榜单中的排名和数据
        Map<String, Object> currentUserRanking = leaderboardService.getCurrentUserRanking(seasonId);
        result.put("currentUserRanking", currentUserRanking);
        
        return result;
    }
}