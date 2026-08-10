package com.example.service;

import com.example.entity.User;
import com.example.entity.UserRegistrationBan;

import java.util.List;

/**
 * 用户禁止报名服务
 */
public interface UserRegistrationBanService {
    
    /**
     * 禁止报名类型枚举
     */
    enum BanType {
        FUTURE_TOURNAMENTS_BY_LEVEL("往后N届特定等级赛事"),
        FUTURE_SERIES_ALL("往后N个系列所有赛事"),
        SEASON_ALL_BY_LEVEL("本赛季特定等级所有赛事"),
        SEASON_ALL("本赛季所有赛事"),
        YEAR_ALL("本年度所有赛事");
        
        private final String description;
        
        BanType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 创建禁止报名记录
     */
    UserRegistrationBan createBan(Long userId, String banType, String targetLevelCode, 
                                  Long targetSeasonId, Integer targetYear, Integer banCount,
                                  Boolean banFinalTournament, String reason, Long adminUserId);
    
    /**
     * 批量创建禁止报名记录
     */
    List<UserRegistrationBan> createBans(List<Long> userIds, String banType, String targetLevelCode,
                                          Long targetSeasonId, Integer targetYear, Integer banCount,
                                          Boolean banFinalTournament, String reason, Long adminUserId);
    
    /**
     * 检查用户是否被禁止报名指定赛事
     */
    boolean isUserBannedForTournament(Long userId, Long tournamentId);
    
    /**
     * 获取用户的禁止报名记录
     */
    List<UserRegistrationBan> getUserBans(Long userId);
    
    /**
     * 获取所有禁止报名记录
     */
    List<UserRegistrationBan> getAllBans();
    
    /**
     * 解除禁止报名
     */
    void removeBan(Long banId, Long adminUserId);
    
    /**
     * 检查禁止报名是否生效（基于时间范围）
     */
    boolean isBanActive(UserRegistrationBan ban, Long tournamentId);
}