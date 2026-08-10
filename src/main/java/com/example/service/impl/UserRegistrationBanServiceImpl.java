package com.example.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.entity.Series;
import com.example.entity.Tournament;
import com.example.entity.User;
import com.example.entity.UserRegistrationBan;
import com.example.mapper.UserRegistrationBanMapper;
import com.example.service.INotificationService;
import com.example.service.SeasonService;
import com.example.service.SeriesService;
import com.example.service.TournamentService;
import com.example.service.UserRegistrationBanService;
import com.example.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserRegistrationBanServiceImpl implements UserRegistrationBanService {

    private final UserRegistrationBanMapper banMapper;
    private final UserService userService;
    private final TournamentService tournamentService;
    private final SeriesService seriesService;
    private final SeasonService seasonService;
    private final INotificationService notificationService;

    public UserRegistrationBanServiceImpl(UserRegistrationBanMapper banMapper,
                                          UserService userService,
                                          TournamentService tournamentService,
                                          SeriesService seriesService,
                                          SeasonService seasonService,
                                          INotificationService notificationService) {
        this.banMapper = banMapper;
        this.userService = userService;
        this.tournamentService = tournamentService;
        this.seriesService = seriesService;
        this.seasonService = seasonService;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserRegistrationBan createBan(Long userId, String banType, String targetLevelCode,
                                         Long targetSeasonId, Integer targetYear, Integer banCount,
                                         Boolean banFinalTournament, String reason, Long adminUserId) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        User admin = userService.getById(adminUserId);
        if (admin == null) {
            throw new IllegalArgumentException("管理员不存在");
        }

        UserRegistrationBan ban = new UserRegistrationBan();
        ban.setUserId(userId);
        ban.setBanType(banType);
        ban.setTargetLevelCode(targetLevelCode);
        ban.setTargetSeasonId(targetSeasonId);
        ban.setTargetYear(targetYear);
        ban.setBanCount(banCount);
        ban.setBanFinalTournament(banFinalTournament != null && banFinalTournament);
        ban.setReason(reason);
        ban.setCreatedBy(adminUserId);
        ban.setCreatedAt(LocalDateTime.now());
        banMapper.insert(ban);

        // 发送消息通知
        sendBanNotification(List.of(ban), admin);

        return ban;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<UserRegistrationBan> createBans(List<Long> userIds, String banType, String targetLevelCode,
                                                   Long targetSeasonId, Integer targetYear, Integer banCount,
                                                   Boolean banFinalTournament, String reason, Long adminUserId) {
        if (userIds == null || userIds.isEmpty()) {
            throw new IllegalArgumentException("用户列表不能为空");
        }

        User admin = userService.getById(adminUserId);
        if (admin == null) {
            throw new IllegalArgumentException("管理员不存在");
        }

        List<UserRegistrationBan> bans = new ArrayList<>();
        
        // 自动获取当前赛季和年份
        Long currentSeasonId = getCurrentSeasonId();
        Integer currentYear = getCurrentYear();
        
        for (Long userId : userIds) {
            User user = userService.getById(userId);
            if (user == null) {
                continue; // 跳过不存在的用户
            }

            UserRegistrationBan ban = new UserRegistrationBan();
            ban.setUserId(userId);
            ban.setBanType(banType);
            ban.setTargetLevelCode(targetLevelCode);
            
            // 根据禁止类型自动设置赛季和年份
            if ("SEASON_ALL".equals(banType) || "SEASON_ALL_BY_LEVEL".equals(banType)) {
                ban.setTargetSeasonId(currentSeasonId);
            }
            if ("YEAR_ALL".equals(banType)) {
                ban.setTargetYear(currentYear);
            }
            
            // 如果用户手动指定了，则覆盖自动设置的值
            if (targetSeasonId != null) {
                ban.setTargetSeasonId(targetSeasonId);
            }
            if (targetYear != null) {
                ban.setTargetYear(targetYear);
            }
            
            ban.setBanCount(banCount);
            ban.setBanFinalTournament(banFinalTournament != null && banFinalTournament);
            ban.setReason(reason);
            ban.setCreatedBy(adminUserId);
            ban.setCreatedAt(LocalDateTime.now());
            banMapper.insert(ban);
            bans.add(ban);
        }

        // 发送消息通知
        if (!bans.isEmpty()) {
            sendBanNotification(bans, admin);
        }

        return bans;
    }

    @Override
    public boolean isUserBannedForTournament(Long userId, Long tournamentId) {
        if (userId == null || tournamentId == null) {
            return false;
        }

        List<UserRegistrationBan> bans = getUserBans(userId);
        if (bans.isEmpty()) {
            return false;
        }

        Tournament tournament = tournamentService.getById(tournamentId);
        if (tournament == null) {
            return false;
        }

        for (UserRegistrationBan ban : bans) {
            if (isBanActive(ban, tournamentId)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public List<UserRegistrationBan> getUserBans(Long userId) {
        if (userId == null) {
            return List.of();
        }

        List<UserRegistrationBan> bans = banMapper.selectList(Wrappers.<UserRegistrationBan>lambdaQuery()
                .eq(UserRegistrationBan::getUserId, userId)
                .orderByDesc(UserRegistrationBan::getCreatedAt));

        // 填充用户名
        Set<Long> userIds = bans.stream()
                .map(UserRegistrationBan::getUserId)
                .collect(Collectors.toSet());
        if (!userIds.isEmpty()) {
            Map<Long, String> userNames = userService.listByIds(userIds).stream()
                    .filter(u -> u != null)
                    .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
            for (UserRegistrationBan ban : bans) {
                ban.setUsername(userNames.get(ban.getUserId()));
            }
        }

        // 填充管理员用户名
        Set<Long> adminIds = bans.stream()
                .map(UserRegistrationBan::getCreatedBy)
                .collect(Collectors.toSet());
        if (!adminIds.isEmpty()) {
            Map<Long, String> adminNames = userService.listByIds(adminIds).stream()
                    .filter(u -> u != null)
                    .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
            for (UserRegistrationBan ban : bans) {
                ban.setCreatedByUsername(adminNames.get(ban.getCreatedBy()));
            }
        }

        return bans;
    }

    @Override
    public List<UserRegistrationBan> getAllBans() {
        List<UserRegistrationBan> bans = banMapper.selectList(Wrappers.<UserRegistrationBan>lambdaQuery()
                .orderByDesc(UserRegistrationBan::getCreatedAt));

        // 填充用户名和管理员用户名
        Set<Long> userIds = bans.stream()
                .map(UserRegistrationBan::getUserId)
                .collect(Collectors.toSet());
        Set<Long> adminIds = bans.stream()
                .map(UserRegistrationBan::getCreatedBy)
                .collect(Collectors.toSet());

        if (!userIds.isEmpty()) {
            Map<Long, String> userNames = userService.listByIds(userIds).stream()
                    .filter(u -> u != null)
                    .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
            for (UserRegistrationBan ban : bans) {
                ban.setUsername(userNames.get(ban.getUserId()));
            }
        }

        if (!adminIds.isEmpty()) {
            Map<Long, String> adminNames = userService.listByIds(adminIds).stream()
                    .filter(u -> u != null)
                    .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
            for (UserRegistrationBan ban : bans) {
                ban.setCreatedByUsername(adminNames.get(ban.getCreatedBy()));
            }
        }

        return bans;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeBan(Long banId, Long adminUserId) {
        if (banId == null) {
            throw new IllegalArgumentException("禁止记录ID不能为空");
        }

        UserRegistrationBan ban = banMapper.selectById(banId);
        if (ban == null) {
            throw new IllegalArgumentException("禁止记录不存在");
        }

        banMapper.deleteById(banId);
    }

    @Override
    public boolean isBanActive(UserRegistrationBan ban, Long tournamentId) {
        if (ban == null || tournamentId == null) {
            return false;
        }

        Tournament tournament = tournamentService.getById(tournamentId);
        if (tournament == null) {
            return false;
        }

        Series series = tournament.getSeriesId() != null ? seriesService.getById(tournament.getSeriesId()) : null;
        if (series == null) {
            return false;
        }

        com.example.entity.Season season = series.getSeasonId() != null ? seasonService.getById(series.getSeasonId()) : null;
        if (season == null) {
            return false;
        }

        // 根据禁止类型检查
        switch (ban.getBanType()) {
            case "FUTURE_TOURNAMENTS_BY_LEVEL":
                // 往后N届特定等级赛事
                if (ban.getTargetLevelCode() != null && ban.getTargetLevelCode().equals(tournament.getLevelCode())) {
                    // 计算届次：从禁止记录创建时间开始，计算该等级赛事的届次
                    return isWithinTournamentCount(ban, tournament, series, season);
                }
                break;
            case "FUTURE_SERIES_ALL":
                // 往后N个系列所有赛事
                return isWithinSeriesCount(ban, series, season);
            case "SEASON_ALL_BY_LEVEL":
                // 本赛季特定等级所有赛事
                if (ban.getTargetSeasonId() != null && ban.getTargetSeasonId().equals(season.getId()) &&
                    ban.getTargetLevelCode() != null && ban.getTargetLevelCode().equals(tournament.getLevelCode())) {
                    return true;
                }
                break;
            case "SEASON_ALL":
                // 本赛季所有赛事
                if (ban.getTargetSeasonId() != null && ban.getTargetSeasonId().equals(season.getId())) {
                    return true;
                }
                break;
            case "YEAR_ALL":
                // 本年度所有赛事
                if (ban.getTargetYear() != null && ban.getTargetYear().equals(season.getYear())) {
                    return true;
                }
                break;
        }

        return false;
    }

    /**
     * 检查赛事是否在禁止的届次范围内
     */
    private boolean isWithinTournamentCount(UserRegistrationBan ban, Tournament tournament, Series series, com.example.entity.Season season) {
        if (ban.getBanCount() == null || ban.getBanCount() <= 0) {
            return false;
        }

        // 获取从禁止记录创建时间开始的该等级赛事
        LocalDateTime banTime = ban.getCreatedAt();
        List<Tournament> sameLevelTournaments = tournamentService.lambdaQuery()
                .eq(Tournament::getLevelCode, ban.getTargetLevelCode())
                .ge(Tournament::getCreatedAt, banTime)
                .orderByAsc(Tournament::getCreatedAt)
                .list();

        // 计算当前赛事是第几届
        int tournamentIndex = 0;
        for (int i = 0; i < sameLevelTournaments.size(); i++) {
            if (sameLevelTournaments.get(i).getId().equals(tournament.getId())) {
                tournamentIndex = i + 1;
                break;
            }
        }

        return tournamentIndex <= ban.getBanCount();
    }

    /**
     * 检查赛事是否在禁止的系列数范围内
     */
    private boolean isWithinSeriesCount(UserRegistrationBan ban, Series series, com.example.entity.Season season) {
        if (ban.getBanCount() == null || ban.getBanCount() <= 0) {
            return false;
        }

        // 获取从禁止记录创建时间开始的系列
        LocalDateTime banTime = ban.getCreatedAt();
        List<Series> seriesList = seriesService.lambdaQuery()
                .ge(Series::getCreatedAt, banTime)
                .orderByAsc(Series::getCreatedAt)
                .list();

        // 计算当前系列是第几个
        int seriesIndex = 0;
        for (int i = 0; i < seriesList.size(); i++) {
            if (seriesList.get(i).getId().equals(series.getId())) {
                seriesIndex = i + 1;
                break;
            }
        }

        return seriesIndex <= ban.getBanCount();
    }

    private void sendBanNotification(List<UserRegistrationBan> bans, User admin) {
        if (bans.isEmpty()) {
            return;
        }

        // 生成通知内容
        StringBuilder content = new StringBuilder();
        content.append("管理员 **").append(admin.getUsername()).append("** 对以下用户执行了禁止报名操作：\n\n");

        for (UserRegistrationBan ban : bans) {
            User user = userService.getById(ban.getUserId());
            if (user != null) {
                String banTypeDesc = getBanTypeDescription(ban);
                content.append("- **").append(user.getUsername()).append("**：")
                       .append(banTypeDesc)
                       .append("\n  理由：").append(ban.getReason()).append("\n\n");
            }
        }

        content.append("发布时间：").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        // 发送系统通知
        notificationService.sendSystemNotification(
                "用户禁止报名通报",
                content.toString(),
                "USER_REGISTRATION_BAN",
                null
        );
    }

    private String getBanTypeDescription(UserRegistrationBan ban) {
        String baseDesc = "";
        switch (ban.getBanType()) {
            case "FUTURE_TOURNAMENTS_BY_LEVEL":
                baseDesc = "禁止报名往后" + ban.getBanCount() + "届" + ban.getTargetLevelCode() + "赛事";
                break;
            case "FUTURE_SERIES_ALL":
                baseDesc = "禁止报名往后" + ban.getBanCount() + "个系列所有赛事";
                break;
            case "SEASON_ALL_BY_LEVEL":
                baseDesc = "禁止报名本赛季所有" + ban.getTargetLevelCode() + "赛事";
                break;
            case "SEASON_ALL":
                baseDesc = "禁止报名本赛季所有赛事";
                break;
            case "YEAR_ALL":
                baseDesc = "禁止报名本年度所有赛事";
                break;
        }

        if (Boolean.TRUE.equals(ban.getBanFinalTournament())) {
            baseDesc += "，并不准被选入赛季总决赛/年终总决赛";
        }

        return baseDesc;
    }

    /**
     * 获取当前赛季ID
     */
    private Long getCurrentSeasonId() {
        List<com.example.entity.Season> seasons = seasonService.lambdaQuery()
                .orderByDesc(com.example.entity.Season::getCreatedAt)
                .list();
        return seasons.isEmpty() ? null : seasons.get(0).getId();
    }

    /**
     * 获取当前年份
     */
    private Integer getCurrentYear() {
        return java.time.LocalDateTime.now().getYear();
    }
}