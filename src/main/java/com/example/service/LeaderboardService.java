package com.example.service;

import com.example.dto.RankingLeaderboardEntry;

import java.util.List;
import java.util.Map;

/**
 * 排行榜服务
 */
public interface LeaderboardService {
    /**
     * 获取胜率排行榜
     * @param seasonId 赛季ID，null 表示历史数据
     * @param limit 返回数量
     */
    List<RankingLeaderboardEntry> getWinRateLeaderboard(Long seasonId, Integer limit);

    /**
     * 获取单局最高分排行榜
     * @param seasonId 赛季ID，null 表示历史数据
     * @param limit 返回数量
     */
    List<RankingLeaderboardEntry> getHighestSetScoreLeaderboard(Long seasonId, Integer limit);

    /**
     * 获取偷分次数排行榜
     * @param seasonId 赛季ID，null 表示历史数据
     * @param limit 返回数量
     */
    List<RankingLeaderboardEntry> getStealCountLeaderboard(Long seasonId, Integer limit);

    /**
     * 获取偷分最高分排行榜
     * @param seasonId 赛季ID，null 表示历史数据
     * @param limit 返回数量
     */
    List<RankingLeaderboardEntry> getHighestStealScoreLeaderboard(Long seasonId, Integer limit);

    /**
     * 获取当前用户在各榜单中的排名和数据
     * @param seasonId 赛季ID，null 表示历史数据
     * @return 包含当前用户各榜单排名的Map
     */
    Map<String, Object> getCurrentUserRanking(Long seasonId);

    /**
     * 获取指定用户在各榜单中的排名和数据
     * @param userId 用户ID
     * @param seasonId 赛季ID，null 表示历史数据
     * @return 包含指定用户各榜单排名的Map
     */
    Map<String, Object> getCurrentUserRankingForUser(Long userId, Long seasonId);
}