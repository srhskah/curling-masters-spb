package com.example.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.dto.RankingLeaderboardEntry;
import com.example.entity.Match;
import com.example.entity.SetScore;
import com.example.entity.User;
import com.example.mapper.MatchMapper;
import com.example.mapper.SetScoreMapper;
import com.example.mapper.UserMapper;
import com.example.service.LeaderboardService;
import com.example.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 排行榜服务实现
 */
@Service
@RequiredArgsConstructor
public class LeaderboardServiceImpl implements LeaderboardService {

    private final JdbcTemplate jdbcTemplate;
    private final UserMapper userMapper;
    private final MatchMapper matchMapper;
    private final SetScoreMapper setScoreMapper;
    private final UserService userService;

    @Override
    public List<RankingLeaderboardEntry> getWinRateLeaderboard(Long seasonId, Integer limit) {
        String sql;
        if (seasonId == null) {
            sql = "SELECT m.player1_id as user_id, COUNT(*) as total_matches, SUM(CASE WHEN m.winner_id = m.player1_id THEN 1 ELSE 0 END) as wins FROM `match` m WHERE m.status = 2 AND m.player1_id IS NOT NULL GROUP BY m.player1_id HAVING total_matches >= 5 ORDER BY (wins * 1.0 / total_matches) DESC";
        } else {
            sql = "SELECT m.player1_id as user_id, COUNT(*) as total_matches, SUM(CASE WHEN m.winner_id = m.player1_id THEN 1 ELSE 0 END) as wins FROM `match` m INNER JOIN tournament t ON m.tournament_id = t.id INNER JOIN series s ON t.series_id = s.id WHERE m.status = 2 AND m.player1_id IS NOT NULL AND s.season_id = ? GROUP BY m.player1_id HAVING total_matches >= 5 ORDER BY (wins * 1.0 / total_matches) DESC";
        }

        System.out.println("=== 胜率排行榜 SQL ===");
        System.out.println("seasonId: " + seasonId);
        System.out.println("limit: " + limit);
        System.out.println("SQL: " + sql);

        List<Map<String, Object>> results;
        if (limit == null) {
            results = seasonId == null 
                ? jdbcTemplate.queryForList(sql)
                : jdbcTemplate.queryForList(sql, seasonId);
        } else {
            sql += " LIMIT ?";
            results = seasonId == null 
                ? jdbcTemplate.queryForList(sql, limit)
                : jdbcTemplate.queryForList(sql, seasonId, limit);
        }

        System.out.println("查询结果数量: " + results.size());

        List<RankingLeaderboardEntry> entries = new ArrayList<>();
        int rank = 1;
        for (Map<String, Object> row : results) {
            Long userId = ((Number) row.get("user_id")).longValue();
            Long totalMatches = ((Number) row.get("total_matches")).longValue();
            Long wins = ((Number) row.get("wins")).longValue();
            double winRate = (double) wins / totalMatches;

            User user = userMapper.selectById(userId);
            if (user != null) {
                RankingLeaderboardEntry entry = new RankingLeaderboardEntry();
                entry.setRank(rank++);
                entry.setUserId(userId);
                entry.setUsername(user.getUsername());
                entry.setValue(winRate);
                entry.setDisplayValue(String.format("%.1f%% (%d/%d)", winRate * 100, wins, totalMatches));
                entries.add(entry);
            }
        }
        return entries;
    }

    @Override
    public List<RankingLeaderboardEntry> getHighestSetScoreLeaderboard(Long seasonId, Integer limit) {
        String sql;
        if (seasonId == null) {
            sql = "SELECT user_id, MAX(score) as max_score, MIN(is_x) as min_is_x FROM (SELECT m.player1_id as user_id, ss.player1_score as score, COALESCE(ss.player1_is_x, 0) as is_x FROM set_score ss INNER JOIN `match` m ON ss.match_id = m.id WHERE ss.player1_score IS NOT NULL AND m.player1_id IS NOT NULL UNION ALL SELECT m.player2_id as user_id, ss.player2_score as score, COALESCE(ss.player2_is_x, 0) as is_x FROM set_score ss INNER JOIN `match` m ON ss.match_id = m.id WHERE ss.player2_score IS NOT NULL AND m.player2_id IS NOT NULL) t GROUP BY user_id ORDER BY max_score DESC, min_is_x ASC";
        } else {
            sql = "SELECT user_id, MAX(score) as max_score, MIN(is_x) as min_is_x FROM (SELECT m.player1_id as user_id, ss.player1_score as score, COALESCE(ss.player1_is_x, 0) as is_x FROM set_score ss INNER JOIN `match` m ON ss.match_id = m.id INNER JOIN tournament t ON m.tournament_id = t.id INNER JOIN series s ON t.series_id = s.id WHERE ss.player1_score IS NOT NULL AND m.player1_id IS NOT NULL AND s.season_id = ? UNION ALL SELECT m.player2_id as user_id, ss.player2_score as score, COALESCE(ss.player2_is_x, 0) as is_x FROM set_score ss INNER JOIN `match` m ON ss.match_id = m.id INNER JOIN tournament t ON m.tournament_id = t.id INNER JOIN series s ON t.series_id = s.id WHERE ss.player2_score IS NOT NULL AND m.player2_id IS NOT NULL AND s.season_id = ?) t GROUP BY user_id ORDER BY max_score DESC, min_is_x ASC";
        }

        List<Map<String, Object>> results;
        if (limit == null) {
            results = seasonId == null 
                ? jdbcTemplate.queryForList(sql)
                : jdbcTemplate.queryForList(sql, seasonId, seasonId);
        } else {
            sql += " LIMIT ?";
            results = seasonId == null 
                ? jdbcTemplate.queryForList(sql, limit)
                : jdbcTemplate.queryForList(sql, seasonId, seasonId, limit);
        }

        List<RankingLeaderboardEntry> entries = new ArrayList<>();
        int rank = 1;
        for (Map<String, Object> row : results) {
            Long userId = ((Number) row.get("user_id")).longValue();
            Integer score = ((Number) row.get("max_score")).intValue();
            Boolean isX = ((Number) row.get("min_is_x")).intValue() == 1;

            User user = userMapper.selectById(userId);
            if (user != null) {
                RankingLeaderboardEntry entry = new RankingLeaderboardEntry();
                entry.setRank(rank++);
                entry.setUserId(userId);
                entry.setUsername(user.getUsername());
                entry.setValue((double) score);
                entry.setIntValue(score);
                entry.setDisplayValue(isX ? score + " (X)" : String.valueOf(score));
                entries.add(entry);
            }
        }
        return entries;
    }

    @Override
    public List<RankingLeaderboardEntry> getStealCountLeaderboard(Long seasonId, Integer limit) {
        String sql;
        if (seasonId == null) {
            sql = "SELECT user_id, COUNT(*) as steal_count FROM (SELECT CASE WHEN ss.hammer_player_id = m.player2_id THEN m.player1_id WHEN ss.hammer_player_id = m.player1_id THEN m.player2_id END as user_id FROM set_score ss INNER JOIN `match` m ON ss.match_id = m.id WHERE ss.hammer_player_id IS NOT NULL AND ss.hammer_player_id <> 0 AND CASE WHEN ss.hammer_player_id = m.player2_id THEN ss.player1_score WHEN ss.hammer_player_id = m.player1_id THEN ss.player2_score END > 0) t WHERE user_id IS NOT NULL GROUP BY user_id ORDER BY steal_count DESC";
        } else {
            sql = "SELECT user_id, COUNT(*) as steal_count FROM (SELECT CASE WHEN ss.hammer_player_id = m.player2_id THEN m.player1_id WHEN ss.hammer_player_id = m.player1_id THEN m.player2_id END as user_id FROM set_score ss INNER JOIN `match` m ON ss.match_id = m.id INNER JOIN tournament t ON m.tournament_id = t.id INNER JOIN series s ON t.series_id = s.id WHERE ss.hammer_player_id IS NOT NULL AND ss.hammer_player_id <> 0 AND s.season_id = ? AND CASE WHEN ss.hammer_player_id = m.player2_id THEN ss.player1_score WHEN ss.hammer_player_id = m.player1_id THEN ss.player2_score END > 0) t WHERE user_id IS NOT NULL GROUP BY user_id ORDER BY steal_count DESC";
        }

        List<Map<String, Object>> results;
        if (limit == null) {
            results = seasonId == null 
                ? jdbcTemplate.queryForList(sql)
                : jdbcTemplate.queryForList(sql, seasonId);
        } else {
            sql += " LIMIT ?";
            results = seasonId == null 
                ? jdbcTemplate.queryForList(sql, limit)
                : jdbcTemplate.queryForList(sql, seasonId, limit);
        }

        List<RankingLeaderboardEntry> entries = new ArrayList<>();
        int rank = 1;
        for (Map<String, Object> row : results) {
            Long userId = ((Number) row.get("user_id")).longValue();
            Long stealCount = ((Number) row.get("steal_count")).longValue();

            User user = userMapper.selectById(userId);
            if (user != null) {
                RankingLeaderboardEntry entry = new RankingLeaderboardEntry();
                entry.setRank(rank++);
                entry.setUserId(userId);
                entry.setUsername(user.getUsername());
                entry.setValue((double) stealCount);
                entry.setIntValue(stealCount.intValue());
                entry.setDisplayValue(String.valueOf(stealCount));
                entries.add(entry);
            }
        }
        return entries;
    }

    @Override
    public List<RankingLeaderboardEntry> getHighestStealScoreLeaderboard(Long seasonId, Integer limit) {
        String sql;
        if (seasonId == null) {
            sql = "SELECT user_id, MAX(steal_score) as max_steal_score FROM (SELECT CASE WHEN ss.hammer_player_id = m.player2_id THEN m.player1_id WHEN ss.hammer_player_id = m.player1_id THEN m.player2_id END as user_id, CASE WHEN ss.hammer_player_id = m.player2_id THEN ss.player1_score WHEN ss.hammer_player_id = m.player1_id THEN ss.player2_score END as steal_score FROM set_score ss INNER JOIN `match` m ON ss.match_id = m.id WHERE ss.hammer_player_id IS NOT NULL AND ss.hammer_player_id <> 0 AND CASE WHEN ss.hammer_player_id = m.player2_id THEN ss.player1_score WHEN ss.hammer_player_id = m.player1_id THEN ss.player2_score END > 0) t WHERE user_id IS NOT NULL GROUP BY user_id ORDER BY max_steal_score DESC";
        } else {
            sql = "SELECT user_id, MAX(steal_score) as max_steal_score FROM (SELECT CASE WHEN ss.hammer_player_id = m.player2_id THEN m.player1_id WHEN ss.hammer_player_id = m.player1_id THEN m.player2_id END as user_id, CASE WHEN ss.hammer_player_id = m.player2_id THEN ss.player1_score WHEN ss.hammer_player_id = m.player1_id THEN ss.player2_score END as steal_score FROM set_score ss INNER JOIN `match` m ON ss.match_id = m.id INNER JOIN tournament t ON m.tournament_id = t.id INNER JOIN series s ON t.series_id = s.id WHERE ss.hammer_player_id IS NOT NULL AND ss.hammer_player_id <> 0 AND s.season_id = ? AND CASE WHEN ss.hammer_player_id = m.player2_id THEN ss.player1_score WHEN ss.hammer_player_id = m.player1_id THEN ss.player2_score END > 0) t WHERE user_id IS NOT NULL GROUP BY user_id ORDER BY max_steal_score DESC";
        }

        List<Map<String, Object>> results;
        if (limit == null) {
            results = seasonId == null 
                ? jdbcTemplate.queryForList(sql)
                : jdbcTemplate.queryForList(sql, seasonId);
        } else {
            sql += " LIMIT ?";
            results = seasonId == null 
                ? jdbcTemplate.queryForList(sql, limit)
                : jdbcTemplate.queryForList(sql, seasonId, limit);
        }

        List<RankingLeaderboardEntry> entries = new ArrayList<>();
        int rank = 1;
        for (Map<String, Object> row : results) {
            Long userId = ((Number) row.get("user_id")).longValue();
            Integer maxStealScore = ((Number) row.get("max_steal_score")).intValue();

            User user = userMapper.selectById(userId);
            if (user != null) {
                RankingLeaderboardEntry entry = new RankingLeaderboardEntry();
                entry.setRank(rank++);
                entry.setUserId(userId);
                entry.setUsername(user.getUsername());
                entry.setValue((double) maxStealScore);
                entry.setIntValue(maxStealScore);
                entry.setDisplayValue(String.valueOf(maxStealScore));
                entries.add(entry);
            }
        }
        return entries;
    }

    @Override
    public Map<String, Object> getCurrentUserRanking(Long seasonId) {
        // 获取当前登录用户
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return null;
        }

        Map<String, Object> result = new HashMap<>();
        
        // 获取全量数据以计算当前用户的排名
        List<RankingLeaderboardEntry> winRateAll = getWinRateLeaderboard(seasonId, null);
        List<RankingLeaderboardEntry> highestSetScoreAll = getHighestSetScoreLeaderboard(seasonId, null);
        List<RankingLeaderboardEntry> stealCountAll = getStealCountLeaderboard(seasonId, null);
        List<RankingLeaderboardEntry> highestStealScoreAll = getHighestStealScoreLeaderboard(seasonId, null);
        
        // 查找当前用户在各榜单中的排名
        result.put("winRate", findUserInLeaderboard(winRateAll, currentUserId));
        result.put("highestSetScore", findUserInLeaderboard(highestSetScoreAll, currentUserId));
        result.put("stealCount", findUserInLeaderboard(stealCountAll, currentUserId));
        result.put("highestStealScore", findUserInLeaderboard(highestStealScoreAll, currentUserId));
        
        return result;
    }

    private RankingLeaderboardEntry findUserInLeaderboard(List<RankingLeaderboardEntry> leaderboard, Long userId) {
        for (RankingLeaderboardEntry entry : leaderboard) {
            if (entry.getUserId().equals(userId)) {
                return entry;
            }
        }
        return null;
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            User user = userService.findByUsername(auth.getName());
            return user != null ? user.getId() : null;
        }
        return null;
    }

    @Override
    public Map<String, Object> getCurrentUserRankingForUser(Long userId, Long seasonId) {
        if (userId == null) {
            return null;
        }

        Map<String, Object> result = new HashMap<>();
        
        // 获取全量数据以计算指定用户的排名
        List<RankingLeaderboardEntry> winRateAll = getWinRateLeaderboard(seasonId, null);
        List<RankingLeaderboardEntry> highestSetScoreAll = getHighestSetScoreLeaderboard(seasonId, null);
        List<RankingLeaderboardEntry> stealCountAll = getStealCountLeaderboard(seasonId, null);
        List<RankingLeaderboardEntry> highestStealScoreAll = getHighestStealScoreLeaderboard(seasonId, null);
        
        // 查找指定用户在各榜单中的排名
        result.put("winRate", findUserInLeaderboard(winRateAll, userId));
        result.put("highestSetScore", findUserInLeaderboard(highestSetScoreAll, userId));
        result.put("stealCount", findUserInLeaderboard(stealCountAll, userId));
        result.put("highestStealScore", findUserInLeaderboard(highestStealScoreAll, userId));
        
        return result;
    }
}