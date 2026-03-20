package com.example.service.impl;

import com.example.dto.RankingEntry;
import com.example.entity.Season;
import com.example.entity.Series;
import com.example.entity.User;
import com.example.mapper.RankingMapper;
import com.example.service.RankingService;
import com.example.service.SeasonService;
import com.example.service.SeriesService;
import com.example.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RankingServiceImpl implements RankingService {

    @Autowired private RankingMapper rankingMapper;
    @Autowired private SeriesService seriesService;
    @Autowired private SeasonService seasonService;
    @Autowired private UserService userService;

    private static final String WITHDRAWN_USERNAME = "退赛";
    private static final List<String> FINALS_LEVEL_CODES = List.of("CM Fi-A", "CM Fi-B", "CM An-Fi");

    @Override
    public List<RankingEntry> getTotalRanking(Integer limit) {
        // 总排名：
        // - 取“总体最近20个系列”范围内，各选手“最佳10个系列”累加（同系列取最高 max）
        // - 排除“赛季总决赛 / 年终总决赛所在系列”后取 Top10（避免占用名额）
        // - 决赛/年终加分：仅取最高值 MAX【max(A,B), 年终】
        List<Series> recentSeries = seriesService.lambdaQuery()
                .orderByDesc(Series::getCreatedAt)
                .last("LIMIT 20")
                .list();
        List<Long> seriesIds = recentSeries.stream().map(Series::getId).collect(Collectors.toList());
        if (seriesIds.isEmpty()) return List.of();

        // 1) 排除“总决赛/年终总决赛所在系列”
        List<Long> finalsSeriesIds = rankingMapper.selectSeriesIdsHavingLevels(seriesIds, FINALS_LEVEL_CODES);

        // 2) 常规系列：同系列取最高，再取最佳10个系列累加
        List<Map<String, Object>> rows = rankingMapper.selectUserSeriesMaxPointsExcludingSeriesIds(seriesIds, finalsSeriesIds);
        Map<Long, List<Integer>> userSeriesPoints = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long userId = toLong(row.get("userId"));
            Integer seriesPoints = toInt(row.get("seriesPoints"));
            if (userId == null || seriesPoints == null) continue;
            userSeriesPoints.computeIfAbsent(userId, k -> new ArrayList<>()).add(seriesPoints);
        }

        Map<Long, Integer> userTotal = new HashMap<>();
        for (Map.Entry<Long, List<Integer>> e : userSeriesPoints.entrySet()) {
            List<Integer> pts = e.getValue();
            pts.sort(Comparator.reverseOrder());
            int sum = 0;
            for (int i = 0; i < Math.min(10, pts.size()); i++) {
                sum += pts.get(i);
            }
            userTotal.put(e.getKey(), sum);
        }

        // 3) 决赛/年终：只取最大的一项加分（不占10系列名额）
        Map<Long, Integer> finalsABMax = new HashMap<>();
        List<Map<String, Object>> finalsA = rankingMapper.selectUserTotalPointsForLevels(seriesIds, List.of("CM Fi-A"));
        for (Map<String, Object> row : finalsA) {
            Long userId = toLong(row.get("userId"));
            Integer pts = toInt(row.get("totalPoints"));
            if (userId == null || pts == null) continue;
            finalsABMax.merge(userId, pts, Math::max);
        }
        List<Map<String, Object>> finalsB = rankingMapper.selectUserTotalPointsForLevels(seriesIds, List.of("CM Fi-B"));
        for (Map<String, Object> row : finalsB) {
            Long userId = toLong(row.get("userId"));
            Integer pts = toInt(row.get("totalPoints"));
            if (userId == null || pts == null) continue;
            finalsABMax.merge(userId, pts, Math::max);
        }

        Map<Long, Integer> yearFinalPoints = new HashMap<>();
        List<Map<String, Object>> yearFinal = rankingMapper.selectUserTotalPointsForLevels(seriesIds, List.of("CM An-Fi"));
        for (Map<String, Object> row : yearFinal) {
            Long userId = toLong(row.get("userId"));
            Integer pts = toInt(row.get("totalPoints"));
            if (userId == null || pts == null) continue;
            yearFinalPoints.put(userId, pts);
        }

        Set<Long> bonusUserIds = new HashSet<>();
        bonusUserIds.addAll(finalsABMax.keySet());
        bonusUserIds.addAll(yearFinalPoints.keySet());
        for (Long uid : bonusUserIds) {
            int ab = finalsABMax.getOrDefault(uid, 0);
            int yf = yearFinalPoints.getOrDefault(uid, 0);
            int bonus = Math.max(ab, yf);
            if (bonus > 0) userTotal.merge(uid, bonus, Integer::sum);
        }

        if (userTotal.isEmpty()) return List.of();

        List<Long> userIds = new ArrayList<>(userTotal.keySet());
        Map<Long, User> usersById = userIds.isEmpty()
                ? Map.of()
                : userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<RankingEntry> result = userTotal.entrySet().stream()
                .map(e -> {
                    User u = usersById.get(e.getKey());
                    String name = u != null ? u.getUsername() : "未知";
                    return new RankingEntry(e.getKey(), name, e.getValue());
                })
                .filter(e -> e.getUsername() == null || !WITHDRAWN_USERNAME.equals(e.getUsername()))
                // 总排名开关：用户可选择不计入总排名
                .filter(e -> {
                    User u = usersById.get(e.getUserId());
                    if (u == null) return true;
                    Boolean include = u.getIncludeInTotalRanking();
                    return include == null || include;
                })
                .sorted(Comparator.comparingInt(RankingEntry::getPoints).reversed()
                        .thenComparing(RankingEntry::getUsername))
                .collect(Collectors.toList());

        if (limit != null && limit > 0 && result.size() > limit) {
            return result.subList(0, limit);
        }
        return result;
    }

    @Override
    public List<RankingEntry> getSeasonRanking(Long seasonId, Integer limit) {
        // 赛季排名：
        // - 赛季积分 = 本赛季【排除总决赛所在系列】后，各选手“最佳10个系列”累加（同系列取最高 max）
        //          + MAX【（如有）赛季总决赛（A/B）积分，（如有）年终总决赛积分】
        if (seasonId == null) return List.of();
        List<Long> seriesIds = seriesService.lambdaQuery()
                .eq(Series::getSeasonId, seasonId)
                .orderByAsc(Series::getSequence)
                .list()
                .stream()
                .map(Series::getId)
                .collect(Collectors.toList());
        if (seriesIds.isEmpty()) return List.of();

        // 1) 排除“总决赛/年终总决赛所在系列”
        List<Long> finalsSeriesIds = rankingMapper.selectSeriesIdsHavingLevels(seriesIds, FINALS_LEVEL_CODES);

        // 2) 常规系列：同系列取最高，再取最佳10个系列累加
        List<Map<String, Object>> rows = rankingMapper.selectUserSeriesMaxPointsExcludingSeriesIds(seriesIds, finalsSeriesIds);
        Map<Long, List<Integer>> userSeriesPoints = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long userId = toLong(row.get("userId"));
            Integer seriesPoints = toInt(row.get("seriesPoints"));
            if (userId == null || seriesPoints == null) continue;
            userSeriesPoints.computeIfAbsent(userId, k -> new ArrayList<>()).add(seriesPoints);
        }

        Map<Long, Integer> userTotal = new HashMap<>();
        for (Map.Entry<Long, List<Integer>> e : userSeriesPoints.entrySet()) {
            List<Integer> pts = e.getValue();
            pts.sort(Comparator.reverseOrder());
            int sum = 0;
            for (int i = 0; i < Math.min(10, pts.size()); i++) {
                sum += pts.get(i);
            }
            userTotal.put(e.getKey(), sum);
        }

        // 3) 总决赛/年终总决赛：只取最大的一项（不占10系列名额）
        // MAX【max(A,B), 年终】
        Map<Long, Integer> finalsABMax = new HashMap<>();
        List<Map<String, Object>> finalsA = rankingMapper.selectUserTotalPointsForLevels(seriesIds, List.of("CM Fi-A"));
        for (Map<String, Object> row : finalsA) {
            Long userId = toLong(row.get("userId"));
            Integer pts = toInt(row.get("totalPoints"));
            if (userId == null || pts == null) continue;
            finalsABMax.merge(userId, pts, Math::max);
        }
        List<Map<String, Object>> finalsB = rankingMapper.selectUserTotalPointsForLevels(seriesIds, List.of("CM Fi-B"));
        for (Map<String, Object> row : finalsB) {
            Long userId = toLong(row.get("userId"));
            Integer pts = toInt(row.get("totalPoints"));
            if (userId == null || pts == null) continue;
            finalsABMax.merge(userId, pts, Math::max);
        }

        Map<Long, Integer> yearFinalPoints = new HashMap<>();
        List<Map<String, Object>> yearFinal = rankingMapper.selectUserTotalPointsForLevels(seriesIds, List.of("CM An-Fi"));
        for (Map<String, Object> row : yearFinal) {
            Long userId = toLong(row.get("userId"));
            Integer pts = toInt(row.get("totalPoints"));
            if (userId == null || pts == null) continue;
            yearFinalPoints.put(userId, pts);
        }

        Set<Long> bonusUserIds = new HashSet<>();
        bonusUserIds.addAll(finalsABMax.keySet());
        bonusUserIds.addAll(yearFinalPoints.keySet());
        for (Long uid : bonusUserIds) {
            int ab = finalsABMax.getOrDefault(uid, 0);
            int yf = yearFinalPoints.getOrDefault(uid, 0);
            int bonus = Math.max(ab, yf);
            if (bonus > 0) userTotal.merge(uid, bonus, Integer::sum);
        }

        if (userTotal.isEmpty()) return List.of();

        List<Long> userIds = new ArrayList<>(userTotal.keySet());
        Map<Long, User> usersById = userIds.isEmpty()
                ? Map.of()
                : userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<RankingEntry> result = userTotal.entrySet().stream()
                .map(e -> {
                    User u = usersById.get(e.getKey());
                    String name = u != null ? u.getUsername() : "未知";
                    return new RankingEntry(e.getKey(), name, e.getValue());
                })
                .filter(e -> e.getUsername() == null || !WITHDRAWN_USERNAME.equals(e.getUsername()))
                .sorted(Comparator.comparingInt(RankingEntry::getPoints).reversed()
                        .thenComparing(RankingEntry::getUsername))
                .collect(Collectors.toList());

        if (limit != null && limit > 0 && result.size() > limit) {
            return result.subList(0, limit);
        }
        return result;
    }

    @Override
    public List<RankingEntry> getSeasonRankingAllSeasonsMerged(Integer limit) {
        // 兼容占位：返回当前赛季排名
        Season current = seasonService.lambdaQuery()
                .orderByDesc(Season::getYear)
                .orderByDesc(Season::getHalf)
                .last("LIMIT 1")
                .one();
        return current == null ? List.of() : getSeasonRanking(current.getId(), limit);
    }

    private List<RankingEntry> computeRankingForSeriesIds(List<Long> seriesIds, Integer limit, boolean applyTotalRankingFlag) {
        if (seriesIds == null || seriesIds.isEmpty()) return List.of();

        List<Map<String, Object>> rows = rankingMapper.selectUserSeriesMaxPoints(seriesIds);

        // userId -> list of seriesPoints
        Map<Long, List<Integer>> userSeriesPoints = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long userId = toLong(row.get("userId"));
            Integer seriesPoints = toInt(row.get("seriesPoints"));
            if (userId == null || seriesPoints == null) continue;
            userSeriesPoints.computeIfAbsent(userId, k -> new ArrayList<>()).add(seriesPoints);
        }

        // top10 sum per user
        Map<Long, Integer> userTotal = new HashMap<>();
        for (Map.Entry<Long, List<Integer>> e : userSeriesPoints.entrySet()) {
            List<Integer> pts = e.getValue();
            pts.sort(Comparator.reverseOrder());
            int sum = 0;
            for (int i = 0; i < Math.min(10, pts.size()); i++) {
                sum += pts.get(i);
            }
            userTotal.put(e.getKey(), sum);
        }

        if (userTotal.isEmpty()) return List.of();

        // load users
        List<Long> userIds = new ArrayList<>(userTotal.keySet());
        Map<Long, User> usersById = userIds.isEmpty()
                ? Map.of()
                : userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<RankingEntry> result = userTotal.entrySet().stream()
                .map(e -> {
                    User u = usersById.get(e.getKey());
                    String name = u != null ? u.getUsername() : "未知";
                    return new RankingEntry(e.getKey(), name, e.getValue());
                })
                // 特殊用户：用户名为“退赛”的不计入任何排名
                .filter(e -> e.getUsername() == null || !WITHDRAWN_USERNAME.equals(e.getUsername()))
                // 总排名开关：用户可选择不计入总排名（只影响总排名，不影响赛季排名）
                .filter(e -> {
                    if (!applyTotalRankingFlag) return true;
                    User u = usersById.get(e.getUserId());
                    if (u == null) return true;
                    Boolean include = u.getIncludeInTotalRanking();
                    return include == null || include;
                })
                .sorted(Comparator.comparingInt(RankingEntry::getPoints).reversed()
                        .thenComparing(RankingEntry::getUsername))
                .collect(Collectors.toList());

        if (limit != null && limit > 0 && result.size() > limit) {
            return result.subList(0, limit);
        }
        return result;
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Long l) return l;
        if (v instanceof Integer i) return i.longValue();
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception ignored) { return null; }
    }

    private static Integer toInt(Object v) {
        if (v == null) return null;
        if (v instanceof Integer i) return i;
        if (v instanceof Long l) return l.intValue();
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception ignored) { return null; }
    }
}

