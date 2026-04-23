package com.example.controller;

import com.example.dto.*;
import com.example.entity.*;
import com.example.mapper.MatchAcceptanceMapper;
import com.example.mapper.MatchScoreEditLogMapper;
import com.example.mapper.TournamentGroupMapper;
import com.example.mapper.TournamentGroupMemberMapper;
import com.example.service.*;
import com.example.mapper.RankingMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/ranking/api")
public class RankingApiController {

    @Autowired private RankingService rankingService;
    @Autowired private SeasonService seasonService;
    @Autowired private SeriesService seriesService;
    @Autowired private TournamentService tournamentService;
    @Autowired private ITournamentLevelService tournamentLevelService;
    @Autowired private UserService userService;
    @Autowired private UserTournamentPointsService userTournamentPointsService;
    @Autowired private com.example.mapper.RankingMapper rankingMapper;
    @Autowired private IMatchService matchService;
    @Autowired private ISetScoreService setScoreService;
    @Autowired private MatchAcceptanceMapper matchAcceptanceMapper;
    @Autowired private MatchScoreEditLogMapper matchScoreEditLogMapper;
    @Autowired private TournamentGroupMapper tournamentGroupMapper;
    @Autowired private TournamentGroupMemberMapper tournamentGroupMemberMapper;
    @Autowired private ITournamentCompetitionService tournamentCompetitionService;
    @Autowired private com.example.service.impl.TournamentRankingRosterService tournamentRankingRosterService;
    @Autowired private GroupRankingCalculator groupRankingCalculator;

    @GetMapping("/total")
    public List<RankingListEntryDto> getTotalRanking(
            @RequestParam(required = false) String limit
    ) {
        Integer parsedLimit = parseLimit(limit);
        List<RankingEntry> entries = rankingService.getTotalRanking(parsedLimit);
        return toRankedList(entries);
    }

    @GetMapping("/season/{seasonId}")
    public List<RankingListEntryDto> getSeasonRanking(
            @PathVariable Long seasonId,
            @RequestParam(required = false) String limit
    ) {
        Integer parsedLimit = parseLimit(limit);
        List<RankingEntry> entries = rankingService.getSeasonRanking(seasonId, parsedLimit);
        return toRankedList(entries);
    }

    /**
     * 开发者公开接口：实时总排名（支持 topN）。
     * - 仅接受整数 topN，范围 1~200（默认 24）
     * - 通过 MyBatis 参数绑定查询，避免 SQL 注入
     */
    @GetMapping("/public/total")
    public Map<String, Object> getPublicTotalRanking(
            @RequestParam(required = false) Integer topN
    ) {
        int top = sanitizeTopN(topN);
        List<RankingEntry> entries = rankingService.getTotalRanking(top);
        return Map.of(
                "scope", "total",
                "topN", top,
                "generatedAt", String.valueOf(java.time.OffsetDateTime.now()),
                "rankings", toRankedList(entries)
        );
    }

    /**
     * 开发者公开接口：实时当前赛季排名（支持 topN）。
     * - 当前赛季定义：year/half 倒序第一条
     * - 仅接受整数 topN，范围 1~200（默认 24）
     */
    @GetMapping("/public/current-season")
    public Map<String, Object> getPublicCurrentSeasonRanking(
            @RequestParam(required = false) Integer topN
    ) {
        int top = sanitizeTopN(topN);
        Season currentSeason = seasonService.lambdaQuery()
                .orderByDesc(Season::getYear)
                .orderByDesc(Season::getHalf)
                .last("LIMIT 1")
                .one();
        if (currentSeason == null || currentSeason.getId() == null) {
            return Map.of(
                    "scope", "current-season",
                    "topN", top,
                    "seasonId", null,
                    "seasonLabel", "",
                    "generatedAt", String.valueOf(java.time.OffsetDateTime.now()),
                    "rankings", List.of()
            );
        }
        String seasonLabel = currentSeason.getYear() + "年" + (Objects.equals(currentSeason.getHalf(), 1) ? "上半年" : "下半年");
        List<RankingEntry> entries = rankingService.getSeasonRanking(currentSeason.getId(), top);
        return Map.of(
                "scope", "current-season",
                "topN", top,
                "seasonId", currentSeason.getId(),
                "seasonLabel", seasonLabel,
                "generatedAt", String.valueOf(java.time.OffsetDateTime.now()),
                "rankings", toRankedList(entries)
        );
    }

    /**
     * 获取某个赛事系列内：所有赛事的排名（含退赛标注）
     */
    @GetMapping("/series/{seriesId}/tournaments")
    public SeriesTournamentRankingDto getSeriesTournamentRankings(@PathVariable Long seriesId) {
        Series series = seriesService.getById(seriesId);
        if (series == null) {
            return new SeriesTournamentRankingDto(seriesId, "", List.of());
        }

        Season season = series.getSeasonId() != null ? seasonService.getById(series.getSeasonId()) : null;
        String seriesLabel = buildSeriesLabel(season, series);

        List<Tournament> tournaments = tournamentService.lambdaQuery()
                .eq(Tournament::getSeriesId, seriesId)
                // Tournament.getCreateTime() 仅为兼容方法，不带字段映射；排序字段使用 createdAt
                .orderByAsc(Tournament::getCreatedAt)
                .list();

        Map<Long, Integer> editionByTournamentId = computeEditionByTournamentId(series.getSeasonId(), tournaments);

        List<TournamentRankingSectionDto> tournamentSections = new ArrayList<>();
        for (Tournament t : tournaments) {
            TournamentLevel level = t.getLevelCode() != null
                    ? tournamentLevelService.lambdaQuery().eq(TournamentLevel::getCode, t.getLevelCode()).one()
                    : null;

            String levelName = level != null ? level.getName() : t.getLevelCode();
            Integer edition = editionByTournamentId.get(t.getId());

            Integer status = t.getStatus();
            String statusLabel = toTournamentStatusLabel(status);

            String tournamentLabel = levelName != null ? levelName : "";
            if (edition != null) {
                tournamentLabel = tournamentLabel + " 第" + edition + "届";
            }

            List<UserTournamentPoints> utps = tournamentRankingRosterService.filterUtpsForDisplay(t.getId(),
                    userTournamentPointsService.lambdaQuery()
                            .eq(UserTournamentPoints::getTournamentId, t.getId())
                            .orderByDesc(UserTournamentPoints::getPoints)
                            .list());

            List<TournamentRankingItemDto> rankings = new ArrayList<>();
            for (int i = 0; i < utps.size(); i++) {
                UserTournamentPoints utp = utps.get(i);
                int rank = i + 1;

                String username;
                boolean withdrawn;
                if (utp.getUserId() == null) {
                    username = "退赛";
                    withdrawn = true;
                } else {
                    User user = userService.getById(utp.getUserId());
                    username = user != null ? user.getUsername() : "未知";
                    withdrawn = "退赛".equals(username);
                }

                Integer points = utp.getPoints();
                rankings.add(new TournamentRankingItemDto(
                        rank,
                        username,
                        points != null ? points : 0,
                        withdrawn
                ));
            }

            tournamentSections.add(new TournamentRankingSectionDto(
                    t.getId(),
                    tournamentLabel,
                    edition,
                    status,
                    statusLabel,
                    rankings
            ));
        }

        return new SeriesTournamentRankingDto(seriesId, seriesLabel, tournamentSections);
    }

    /**
     * 获取某个系列的“积分汇总表”（用于多合一PDF/复制）
     * - 如果本系列有 CM1000：|选手|最终积分|CM1000-{在本赛季的届次}|（如有）CM500-{届次}|（如有）CM250-{届次}|
     * - 如果本系列没有 CM1000：|选手|最终积分|
     *
     * 规则：
     * - 最终积分 = 该系列内所有赛事积分的最大值（同系列最终积分取 max，与排名逻辑一致）
     * - 动态列按赛事生成（同等级可能多届），单元格为该赛事的积分（每个赛事同用户最多一条积分）
     * - 退赛：保留并标注（user_id=NULL 或 username=退赛）
     * - 高亮：最终积分取自哪一列，就在该单元格高亮（bestKey）
     * - 金银铜：系列内任一赛事拿过前三，标记 bestFinish（1/2/3）
     */
    @GetMapping("/series/{seriesId}/summary")
    public Map<String, Object> getSeriesPointsSummary(@PathVariable Long seriesId) {
        Series series = seriesService.getById(seriesId);
        if (series == null) {
            return Map.of(
                    "seriesId", seriesId,
                    "seriesLabel", "",
                    "seasonLabel", "",
                    "seriesName", "",
                    "columns", List.of(),
                    "rows", List.of()
            );
        }

        Season season = series.getSeasonId() != null ? seasonService.getById(series.getSeasonId()) : null;
        String seriesLabel = buildSeriesLabel(season, series);
        String seasonLabel = season == null
                ? ""
                : (season.getYear() + "年" + (season.getHalf() == 1 ? "上半年" : "下半年"));
        String seriesName = buildSeriesDisplayName(series.getSeasonId(), series);

        List<Tournament> tournaments = tournamentService.lambdaQuery()
                .eq(Tournament::getSeriesId, seriesId)
                .orderByAsc(Tournament::getCreatedAt)
                .list();

        Map<Long, Integer> editionByTournamentId = computeEditionByTournamentId(series.getSeasonId(), tournaments);

        boolean hasCm1000 = tournaments.stream().anyMatch(t -> "CM1000".equals(t.getLevelCode()));
        boolean hasFinalA = tournaments.stream().anyMatch(t -> "CM Fi-A".equals(t.getLevelCode()));
        boolean hasFinalB = tournaments.stream().anyMatch(t -> "CM Fi-B".equals(t.getLevelCode()));
        boolean showFinalRank = !hasCm1000;

        // 列：
        // - 若包含 CM1000：展开（CM1000/CM500/CM250）
        // - 若包含赛季总决赛（A/B）：也单独展开为两列（即使没有 CM1000）
        List<SeriesPointsSummaryColumnDto> columns = new ArrayList<>();
        if (hasCm1000) {
            List<Tournament> columnTournaments = tournaments.stream()
                    .filter(t -> t.getId() != null)
                    .filter(t -> {
                        String c = t.getLevelCode();
                        return "CM1000".equals(c) || "CM500".equals(c) || "CM250".equals(c);
                    })
                    .toList();

            // 排序：按等级优先级 -> 再按届次
            columnTournaments = columnTournaments.stream()
                    .sorted(Comparator
                            .comparingInt((Tournament t) -> {
                                String c = t.getLevelCode();
                                if ("CM1000".equals(c)) return 1;
                                if ("CM500".equals(c)) return 2;
                                if ("CM250".equals(c)) return 3;
                                return 9;
                            })
                            .thenComparing(t -> editionByTournamentId.getOrDefault(t.getId(), 9999))
                            .thenComparing(t -> t.getId() != null ? t.getId() : 0L))
                    .toList();

            for (Tournament t : columnTournaments) {
                Integer ed = editionByTournamentId.get(t.getId());
                // 表头去掉“第/届”，仅保留数字
                String label = t.getLevelCode() + "-" + (ed != null ? String.valueOf(ed) : "?");
                String key = "t" + t.getId();
                columns.add(new SeriesPointsSummaryColumnDto(key, label));
            }
        }

        if (hasFinalA || hasFinalB) {
            Map<String, String> levelNameByCode = tournamentLevelService.list().stream()
                    .collect(Collectors.toMap(TournamentLevel::getCode, TournamentLevel::getName, (a, b) -> a));
            List<Tournament> finalsTournaments = tournaments.stream()
                    .filter(t -> t.getId() != null)
                    .filter(t -> "CM Fi-A".equals(t.getLevelCode()) || "CM Fi-B".equals(t.getLevelCode()))
                    .sorted(Comparator
                            .comparingInt((Tournament t) -> "CM Fi-A".equals(t.getLevelCode()) ? 1 : 2)
                            .thenComparing(t -> t.getCreatedAt(), Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(t -> t.getId() != null ? t.getId() : 0L))
                    .toList();
            for (Tournament t : finalsTournaments) {
                String levelCode = t.getLevelCode();
                String label = levelNameByCode.getOrDefault(levelCode, levelCode);
                String key = "t" + t.getId();
                columns.add(new SeriesPointsSummaryColumnDto(key, label));
            }
        }

        // username -> aggregated
        class Agg {
            String username;
            boolean withdrawn;
            int finalPoints;
            String bestKey;
            Integer bestFinish; // 1/2/3
            Map<String, Integer> pointsByKey = new HashMap<>();
            Map<String, Integer> finishByKey = new HashMap<>();
        }

        Map<String, Agg> map = new HashMap<>();
        for (Tournament t : tournaments) {
            if (t.getId() == null) continue;

            List<UserTournamentPoints> utps = tournamentRankingRosterService.filterUtpsForDisplay(t.getId(),
                    userTournamentPointsService.lambdaQuery()
                            .eq(UserTournamentPoints::getTournamentId, t.getId())
                            .orderByDesc(UserTournamentPoints::getPoints)
                            .list());

            for (int i = 0; i < utps.size(); i++) {
                UserTournamentPoints utp = utps.get(i);
                int rankInTournament = i + 1;
                String username;
                boolean withdrawn;
                if (utp.getUserId() == null) {
                    username = "退赛";
                    withdrawn = true;
                } else {
                    User user = userService.getById(utp.getUserId());
                    username = user != null ? user.getUsername() : "未知";
                    withdrawn = "退赛".equals(username);
                }

                int points = utp.getPoints() != null ? utp.getPoints() : 0;
                Agg agg = map.computeIfAbsent(username, k -> {
                    Agg a = new Agg();
                    a.username = username;
                    a.withdrawn = withdrawn;
                    a.finalPoints = 0;
                    a.bestKey = null;
                    a.bestFinish = null;
                    return a;
                });

                agg.withdrawn = agg.withdrawn || withdrawn;
                if (!withdrawn && rankInTournament <= 3) {
                    agg.bestFinish = (agg.bestFinish == null) ? rankInTournament : Math.min(agg.bestFinish, rankInTournament);
                }

                String key = "t" + t.getId();
                agg.pointsByKey.put(key, points);
                agg.finishByKey.put(key, rankInTournament);
                if (points > agg.finalPoints) {
                    agg.finalPoints = points;
                    agg.bestKey = key;
                }
            }
        }

        // 生成排名：退赛不计入排名（finalRank = null）
        List<Agg> sortedAggs = map.values().stream()
                .sorted(Comparator.comparingInt((Agg a) -> a.finalPoints).reversed()
                        .thenComparing(a -> a.username))
                .toList();

        int rankCounter = 0;
        Map<String, Integer> finalRankByUsername = new HashMap<>();
        for (Agg a : sortedAggs) {
            if (a.withdrawn) continue;
            rankCounter++;
            finalRankByUsername.put(a.username, rankCounter);
        }

        // 如果 bestKey 不在 columns（例如最终积分来自其它赛事），则按 columns 顺序尝试找到等值列作为高亮
        List<String> columnKeysInOrder = columns.stream().map(SeriesPointsSummaryColumnDto::getKey).toList();

        List<SeriesPointsSummaryRowV2Dto> rows = sortedAggs.stream()
                .map(a -> {
                    String bestKey = a.bestKey;
                    if (bestKey != null && !columnKeysInOrder.isEmpty() && !columnKeysInOrder.contains(bestKey)) {
                        // fallback: 找到某个列值 == finalPoints 的列
                        for (String ck : columnKeysInOrder) {
                            Integer v = a.pointsByKey.get(ck);
                            if (v != null && v == a.finalPoints) {
                                bestKey = ck;
                                break;
                            }
                        }
                    }
                    return new SeriesPointsSummaryRowV2Dto(
                            a.username,
                            a.withdrawn,
                            finalRankByUsername.get(a.username),
                            a.finalPoints,
                            bestKey,
                            a.bestFinish,
                            a.finishByKey,
                            a.pointsByKey
                    );
                })
                .toList();

        return Map.of(
                "seriesId", seriesId,
                "seriesLabel", seriesLabel,
                "seasonLabel", seasonLabel,
                "seriesName", seriesName,
                "showFinalRank", showFinalRank,
                "columns", columns,
                "rows", rows
        );
    }

    /**
     * 单个赛事排名（用于赛事详情页复制/导出PDF、系列卡片复制/导出PDF）
     */
    @GetMapping("/tournament/{tournamentId}/ranking")
    public Map<String, Object> getTournamentRanking(@PathVariable Long tournamentId) {
        Tournament t = tournamentService.getById(tournamentId);
        if (t == null) {
            return Map.of(
                    "tournamentId", tournamentId,
                    "tournamentLabel", "",
                    "seasonLabel", "",
                    "seasonYear", null,
                    "seasonHalf", null,
                    "levelName", "",
                    "levelCode", "",
                    "edition", null,
                    "rankings", List.of()
            );
        }

        Season season = null;
        if (t.getSeriesId() != null) {
            Series s = seriesService.getById(t.getSeriesId());
            if (s != null && s.getSeasonId() != null) {
                season = seasonService.getById(s.getSeasonId());
            }
        }

        TournamentLevel level = t.getLevelCode() != null
                ? tournamentLevelService.lambdaQuery().eq(TournamentLevel::getCode, t.getLevelCode()).one()
                : null;
        String levelName = level != null ? level.getName() : t.getLevelCode();

        String seasonLabel = season == null ? "" : (season.getYear() + "年" + (season.getHalf() == 1 ? "上半年" : "下半年"));

        Integer edition = null;
        if (season != null && t.getLevelCode() != null) {
            List<Long> seasonSeriesIds = seriesService.lambdaQuery()
                    .eq(Series::getSeasonId, season.getId())
                    .list()
                    .stream()
                    .filter(s -> !isTestSeries(s))
                    .map(Series::getId)
                    .filter(Objects::nonNull)
                    .toList();
            if (!seasonSeriesIds.isEmpty()) {
                List<Tournament> sameLevel = tournamentService.lambdaQuery()
                        .in(Tournament::getSeriesId, seasonSeriesIds)
                        .eq(Tournament::getLevelCode, t.getLevelCode())
                        .list();
                sameLevel.sort(Comparator.comparing(Tournament::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Tournament::getId, Comparator.nullsLast(Comparator.naturalOrder())));
                for (int i = 0; i < sameLevel.size(); i++) {
                    Tournament tt = sameLevel.get(i);
                    if (Objects.equals(tt.getId(), t.getId())) {
                        edition = i + 1;
                        break;
                    }
                }
            }
        }

        List<UserTournamentPoints> utps = tournamentRankingRosterService.filterUtpsForDisplay(tournamentId,
                userTournamentPointsService.lambdaQuery()
                        .eq(UserTournamentPoints::getTournamentId, tournamentId)
                        .orderByDesc(UserTournamentPoints::getPoints)
                        .list());

        Map<Long, Integer> groupOverallRankByUserId = new HashMap<>();
        try {
            Map<String, Object> goData = getTournamentGroupOverallRanking(tournamentId);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> overallRows = (List<Map<String, Object>>) goData.getOrDefault("overallRanking", List.of());
            for (Map<String, Object> row : overallRows) {
                Object uidObj = row.get("userId");
                Object orObj = row.get("overallRank");
                Long uid = uidObj instanceof Number ? ((Number) uidObj).longValue() : null;
                if (uid == null) {
                    continue;
                }
                int gor = orObj instanceof Number ? ((Number) orObj).intValue() : 0;
                if (gor > 0) {
                    groupOverallRankByUserId.put(uid, gor);
                }
            }
        } catch (RuntimeException ignored) {
            // 无小组赛数据等情况下总排名可能不可用
        }

        List<TournamentRankingItemDto> rankings = new ArrayList<>();
        for (int i = 0; i < utps.size(); i++) {
            UserTournamentPoints utp = utps.get(i);
            String username;
            boolean withdrawn;
            if (utp.getUserId() == null) {
                username = "退赛";
                withdrawn = true;
            } else {
                User user = userService.getById(utp.getUserId());
                username = user != null ? user.getUsername() : "未知";
                withdrawn = "退赛".equals(username);
            }
            int points = utp.getPoints() != null ? utp.getPoints() : 0;
            TournamentRankingItemDto dto = new TournamentRankingItemDto(i + 1, username, points, withdrawn);
            if (utp.getUserId() != null) {
                dto.setGroupOverallRank(groupOverallRankByUserId.get(utp.getUserId()));
            }
            rankings.add(dto);
        }

        List<Match> matches = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tournamentId)
                .orderByAsc(Match::getRound)
                .orderByAsc(Match::getId)
                .list();
        List<Long> matchIds = matches.stream().map(Match::getId).filter(Objects::nonNull).toList();
        Map<Long, List<SetScore>> scoresByMatch = matchIds.isEmpty() ? Map.of() : setScoreService.lambdaQuery()
                .in(SetScore::getMatchId, matchIds)
                .orderByAsc(SetScore::getSetNumber)
                .list()
                .stream().collect(Collectors.groupingBy(SetScore::getMatchId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<MatchAcceptance>> acceptsByMatch = matchIds.isEmpty() ? Map.of() : matchAcceptanceMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<MatchAcceptance>lambdaQuery()
                        .in(MatchAcceptance::getMatchId, matchIds)
                        .orderByAsc(MatchAcceptance::getAcceptedAt)
        ).stream().collect(Collectors.groupingBy(MatchAcceptance::getMatchId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<MatchScoreEditLog>> logsByMatch = matchIds.isEmpty() ? Map.of() : matchScoreEditLogMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<MatchScoreEditLog>lambdaQuery()
                        .in(MatchScoreEditLog::getMatchId, matchIds)
                        .orderByAsc(MatchScoreEditLog::getEditedAt)
                        .orderByAsc(MatchScoreEditLog::getId)
        ).stream().collect(Collectors.groupingBy(MatchScoreEditLog::getMatchId, LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> matchDetails = new ArrayList<>();
        for (Match m : matches) {
            User p1 = m.getPlayer1Id() == null ? null : userService.getById(m.getPlayer1Id());
            User p2 = m.getPlayer2Id() == null ? null : userService.getById(m.getPlayer2Id());
            String p1n = p1 != null ? p1.getUsername() : "待定";
            String p2n = p2 != null ? p2.getUsername() : "待定";
            List<SetScore> setScores = scoresByMatch.getOrDefault(m.getId(), List.of());
            int total1 = setScores.stream().mapToInt(s -> s.getPlayer1Score() == null ? 0 : s.getPlayer1Score()).sum();
            int total2 = setScores.stream().mapToInt(s -> s.getPlayer2Score() == null ? 0 : s.getPlayer2Score()).sum();
            String totalText = total1 + ":" + total2;
            List<Map<String, Object>> sets = new ArrayList<>();
            for (SetScore s : setScores) {
                String hammerName = "未设置";
                if (Objects.equals(s.getHammerPlayerId(), m.getPlayer1Id())) hammerName = p1n;
                else if (Objects.equals(s.getHammerPlayerId(), m.getPlayer2Id())) hammerName = p2n;
                Map<String, Object> setRow = new LinkedHashMap<>();
                setRow.put("setNumber", s.getSetNumber());
                setRow.put("hammer", hammerName);
                setRow.put("player1ScoreText", Boolean.TRUE.equals(s.getPlayer1IsX()) ? "X" : String.valueOf(s.getPlayer1Score() == null ? 0 : s.getPlayer1Score()));
                setRow.put("player2ScoreText", Boolean.TRUE.equals(s.getPlayer2IsX()) ? "X" : String.valueOf(s.getPlayer2Score() == null ? 0 : s.getPlayer2Score()));
                sets.add(setRow);
            }
            List<Map<String, Object>> accepts = acceptsByMatch.getOrDefault(m.getId(), List.of()).stream().map(a -> {
                User u = userService.getById(a.getUserId());
                String un = u != null ? u.getUsername() : "未知";
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("username", un);
                row.put("signature", a.getSignature());
                row.put("acceptedAt", a.getAcceptedAt());
                return row;
            }).toList();
            List<Map<String, Object>> editLogs = logsByMatch.getOrDefault(m.getId(), List.of()).stream().map(l -> {
                User u = userService.getById(l.getEditorUserId());
                String un = u != null ? u.getUsername() : "未知";
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("setNumber", l.getSetNumber());
                row.put("editorUsername", un);
                row.put("oldScore", (Boolean.TRUE.equals(l.getOldPlayer1IsX()) ? "X" : String.valueOf(l.getOldPlayer1Score() == null ? 0 : l.getOldPlayer1Score()))
                        + ":" + (Boolean.TRUE.equals(l.getOldPlayer2IsX()) ? "X" : String.valueOf(l.getOldPlayer2Score() == null ? 0 : l.getOldPlayer2Score())));
                row.put("newScore", (Boolean.TRUE.equals(l.getNewPlayer1IsX()) ? "X" : String.valueOf(l.getNewPlayer1Score() == null ? 0 : l.getNewPlayer1Score()))
                        + ":" + (Boolean.TRUE.equals(l.getNewPlayer2IsX()) ? "X" : String.valueOf(l.getNewPlayer2Score() == null ? 0 : l.getNewPlayer2Score())));
                row.put("editedAt", l.getEditedAt());
                return row;
            }).toList();
            Map<String, Object> matchRow = new LinkedHashMap<>();
            matchRow.put("matchId", m.getId());
            matchRow.put("phaseCode", m.getPhaseCode());
            matchRow.put("category", m.getCategory());
            matchRow.put("round", m.getRound());
            matchRow.put("player1Id", m.getPlayer1Id());
            matchRow.put("player2Id", m.getPlayer2Id());
            matchRow.put("player1Name", p1n);
            matchRow.put("player2Name", p2n);
            matchRow.put("totalText", totalText);
            matchRow.put("player1Total", total1);
            matchRow.put("player2Total", total2);
            matchRow.put("sets", sets);
            matchRow.put("acceptances", accepts);
            matchRow.put("editLogs", editLogs);
            matchDetails.add(matchRow);
        }

        return Map.of(
                "tournamentId", tournamentId,
                "tournamentLabel", levelName != null ? levelName : "",
                "seasonLabel", seasonLabel,
                "seasonYear", season != null ? season.getYear() : null,
                "seasonHalf", season != null ? season.getHalf() : null,
                "levelName", levelName != null ? levelName : "",
                "levelCode", t.getLevelCode() != null ? t.getLevelCode() : "",
                "edition", edition,
                "rankings", rankings,
                "matchDetails", matchDetails
        );
    }

    @GetMapping("/tournament/{tournamentId}/user/{userId}/performance")
    public Map<String, Object> getTournamentUserPerformance(@PathVariable Long tournamentId, @PathVariable Long userId) {
        Map<String, Object> data = getTournamentRanking(tournamentId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matchDetails = (List<Map<String, Object>>) data.getOrDefault("matchDetails", List.of());
        List<Map<String, Object>> userMatches = matchDetails.stream()
                .filter(m -> Objects.equals(m.get("player1Id"), userId) || Objects.equals(m.get("player2Id"), userId))
                .toList();
        User user = userService.getById(userId);
        String userName = user != null ? user.getUsername() : ("用户" + userId);
        String tournamentLabel = data.get("tournamentLabel") != null ? String.valueOf(data.get("tournamentLabel")) : ("赛事" + tournamentId);
        return Map.of(
                "tournamentId", tournamentId,
                "userId", userId,
                "username", userName,
                "tournamentLabel", tournamentLabel,
                "title", "赛事单人战绩（" + tournamentLabel + "）",
                "matchDetails", userMatches
        );
    }

    @GetMapping("/match/{matchId}/performance")
    public Map<String, Object> getMatchPerformance(@PathVariable Long matchId) {
        Match m = matchService.getById(matchId);
        if (m == null) {
            return Map.of("matchId", matchId, "title", "单场比赛战绩", "matchDetails", List.of());
        }
        Map<Long, String> uname = userService.list().stream().collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
        List<SetScore> ss = setScoreService.lambdaQuery()
                .eq(SetScore::getMatchId, matchId)
                .orderByAsc(SetScore::getSetNumber)
                .list();
        int t1 = ss.stream().mapToInt(x -> x.getPlayer1Score() == null ? 0 : x.getPlayer1Score()).sum();
        int t2 = ss.stream().mapToInt(x -> x.getPlayer2Score() == null ? 0 : x.getPlayer2Score()).sum();
        String p1n = uname.getOrDefault(m.getPlayer1Id(), "待定");
        String p2n = uname.getOrDefault(m.getPlayer2Id(), "待定");
        List<Map<String, Object>> sets = ss.stream().map(s -> {
            String p1 = Boolean.TRUE.equals(s.getPlayer1IsX()) ? "X" : String.valueOf(s.getPlayer1Score() == null ? 0 : s.getPlayer1Score());
            String p2 = Boolean.TRUE.equals(s.getPlayer2IsX()) ? "X" : String.valueOf(s.getPlayer2Score() == null ? 0 : s.getPlayer2Score());
            String hammer = Objects.equals(s.getHammerPlayerId(), m.getPlayer1Id()) ? p1n :
                    (Objects.equals(s.getHammerPlayerId(), m.getPlayer2Id()) ? p2n : "-");
            return Map.<String, Object>of(
                    "setNumber", s.getSetNumber(),
                    "player1ScoreText", p1,
                    "player2ScoreText", p2,
                    "hammer", hammer
            );
        }).toList();
        List<Map<String, Object>> accepts = matchAcceptanceMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<MatchAcceptance>lambdaQuery()
                        .eq(MatchAcceptance::getMatchId, matchId)
                        .orderByAsc(MatchAcceptance::getAcceptedAt)
        ).stream().map(a -> Map.<String, Object>of(
                "username", uname.getOrDefault(a.getUserId(), "未知"),
                "signature", a.getSignature(),
                "acceptedAt", a.getAcceptedAt()
        )).toList();
        List<Map<String, Object>> editLogs = matchScoreEditLogMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<MatchScoreEditLog>lambdaQuery()
                        .eq(MatchScoreEditLog::getMatchId, matchId)
                        .orderByDesc(MatchScoreEditLog::getEditedAt)
        ).stream().map(l -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("setNumber", l.getSetNumber());
            row.put("editorUsername", uname.getOrDefault(l.getEditorUserId(), "未知"));
            row.put("oldScore", (Boolean.TRUE.equals(l.getOldPlayer1IsX()) ? "X" : String.valueOf(l.getOldPlayer1Score() == null ? 0 : l.getOldPlayer1Score()))
                    + ":" +
                    (Boolean.TRUE.equals(l.getOldPlayer2IsX()) ? "X" : String.valueOf(l.getOldPlayer2Score() == null ? 0 : l.getOldPlayer2Score())));
            row.put("newScore", (Boolean.TRUE.equals(l.getNewPlayer1IsX()) ? "X" : String.valueOf(l.getNewPlayer1Score() == null ? 0 : l.getNewPlayer1Score()))
                    + ":" +
                    (Boolean.TRUE.equals(l.getNewPlayer2IsX()) ? "X" : String.valueOf(l.getNewPlayer2Score() == null ? 0 : l.getNewPlayer2Score())));
            row.put("editedAt", l.getEditedAt());
            return row;
        }).toList();
        String title = "单场比赛战绩（" + (m.getCategory() == null ? "-" : m.getCategory()) + "）";
        Map<String, Object> matchDetail = new LinkedHashMap<>();
        matchDetail.put("matchId", m.getId());
        matchDetail.put("phaseCode", m.getPhaseCode());
        matchDetail.put("category", m.getCategory());
        matchDetail.put("round", m.getRound());
        matchDetail.put("player1Id", m.getPlayer1Id());
        matchDetail.put("player2Id", m.getPlayer2Id());
        matchDetail.put("player1Name", p1n);
        matchDetail.put("player2Name", p2n);
        matchDetail.put("totalText", t1 + ":" + t2);
        matchDetail.put("player1Total", t1);
        matchDetail.put("player2Total", t2);
        matchDetail.put("sets", sets);
        matchDetail.put("acceptances", accepts);
        matchDetail.put("editLogs", editLogs);
        return Map.of(
                "matchId", matchId,
                "tournamentId", m.getTournamentId(),
                "title", title,
                "matchDetails", List.of(matchDetail)
        );
    }

    @GetMapping("/tournament/{tournamentId}/group-ranking")
    public Map<String, Object> getTournamentGroupRanking(@PathVariable Long tournamentId) {
        Tournament t = tournamentService.getById(tournamentId);
        if (t == null) {
            return Map.of("tournamentId", tournamentId, "groups", List.of(), "pseudoGroups", List.of(), "groupMatches", List.of());
        }
        TournamentCompetitionConfig cfg = tournamentCompetitionService.getConfig(tournamentId);
        boolean allowDraw = cfg == null || !Boolean.FALSE.equals(cfg.getGroupAllowDraw());
        int regularSets = (cfg != null && cfg.getGroupStageSets() != null && cfg.getGroupStageSets() > 0) ? cfg.getGroupStageSets() : 8;

        List<TournamentGroup> groups = tournamentGroupMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<TournamentGroup>lambdaQuery()
                        .eq(TournamentGroup::getTournamentId, tournamentId)
                        .orderByAsc(TournamentGroup::getGroupOrder));
        List<TournamentGroupMember> members = tournamentGroupMemberMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<TournamentGroupMember>lambdaQuery()
                        .eq(TournamentGroupMember::getTournamentId, tournamentId));
        Map<Long, List<Long>> memberIdsByGroup = members.stream().collect(Collectors.groupingBy(
                TournamentGroupMember::getGroupId, LinkedHashMap::new, Collectors.mapping(TournamentGroupMember::getUserId, Collectors.toList())
        ));
        List<Match> groupMatches = matchService.lambdaQuery()
                .eq(Match::getTournamentId, tournamentId)
                .eq(Match::getPhaseCode, "GROUP")
                .orderByAsc(Match::getRound)
                .orderByAsc(Match::getId)
                .list();
        List<Long> mids = groupMatches.stream().map(Match::getId).toList();
        Map<Long, List<SetScore>> setByMatch = mids.isEmpty() ? Map.of() : setScoreService.lambdaQuery()
                .in(SetScore::getMatchId, mids).orderByAsc(SetScore::getSetNumber).list()
                .stream().collect(Collectors.groupingBy(SetScore::getMatchId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<MatchAcceptance>> acceptByMatch = mids.isEmpty() ? Map.of() : matchAcceptanceMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<MatchAcceptance>lambdaQuery()
                        .in(MatchAcceptance::getMatchId, mids)
                        .orderByAsc(MatchAcceptance::getAcceptedAt)
        ).stream().collect(Collectors.groupingBy(MatchAcceptance::getMatchId, LinkedHashMap::new, Collectors.toList()));

        Map<Long, String> uname = userService.list().stream().collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
        Map<Long, String> groupNameById = groups.stream().collect(Collectors.toMap(TournamentGroup::getId, TournamentGroup::getGroupName, (a, b) -> a));
        List<Map<String, Object>> groupRows = new ArrayList<>();
        Map<Long, List<Map<String, Object>>> rankingByGroupId = groupRankingCalculator.buildGroupRankingsByMemberIds(
                groups, memberIdsByGroup, uname, groupMatches, setByMatch, allowDraw, regularSets
        );
        List<Map<String, Object>> pseudoGroupRows = groupRankingCalculator.buildPseudoGroupExportRowsAndApplyMainRanks(
                groups, rankingByGroupId, groupMatches, setByMatch, acceptByMatch, uname, allowDraw, regularSets
        );

        for (TournamentGroup g : groups) {
            List<Map<String, Object>> ranking = rankingByGroupId.getOrDefault(g.getId(), List.of());
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("groupId", g.getId());
            one.put("groupName", g.getGroupName());
            one.put("ranking", ranking);
            groupRows.add(one);
        }

        List<Map<String, Object>> groupMatchRows = groupMatches.stream().map(m -> {
            List<SetScore> ss = setByMatch.getOrDefault(m.getId(), List.of());
            int t1 = ss.stream().mapToInt(x -> x.getPlayer1Score() == null ? 0 : x.getPlayer1Score()).sum();
            int t2 = ss.stream().mapToInt(x -> x.getPlayer2Score() == null ? 0 : x.getPlayer2Score()).sum();
            List<MatchAcceptance> ac = acceptByMatch.getOrDefault(m.getId(), List.of());
            String acceptedAt = ac.isEmpty() ? "-" : String.valueOf(ac.get(ac.size() - 1).getAcceptedAt());
            List<Map<String, Object>> sets = buildSetRowsForExport(ss, m, uname);
            String firstHammerName = sets.isEmpty() ? "-" : String.valueOf(sets.get(0).getOrDefault("hammer", "-"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("groupId", m.getGroupId());
            row.put("groupName", groupNameById.getOrDefault(m.getGroupId(), "-"));
            row.put("category", m.getCategory() == null ? "-" : m.getCategory());
            row.put("player1Name", uname.getOrDefault(m.getPlayer1Id(), "待定"));
            row.put("player2Name", uname.getOrDefault(m.getPlayer2Id(), "待定"));
            row.put("score", t1 + ":" + t2);
            row.put("firstHammerName", firstHammerName);
            row.put("sets", sets);
            row.put("acceptedAt", acceptedAt);
            return row;
        }).toList();

        return Map.of(
                "tournamentId", tournamentId,
                "groups", groupRows,
                "pseudoGroups", pseudoGroupRows,
                "groupMatches", groupMatchRows
        );
    }

    @GetMapping("/tournament/{tournamentId}/group-overall-ranking")
    public Map<String, Object> getTournamentGroupOverallRanking(@PathVariable Long tournamentId) {
        Map<String, Object> data = getTournamentGroupRanking(tournamentId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) data.getOrDefault("groups", List.of());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> g : groups) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rs = (List<Map<String, Object>>) g.getOrDefault("ranking", List.of());
            String gname = String.valueOf(g.getOrDefault("groupName", "-"));
            for (Map<String, Object> r : rs) {
                Map<String, Object> one = new LinkedHashMap<>(r);
                one.put("groupName", gname);
                rows.add(one);
            }
        }
        rows.sort((a, b) -> {
            int ar = (int) a.getOrDefault("groupRank", 999), br = (int) b.getOrDefault("groupRank", 999);
            if (ar != br) return Integer.compare(ar, br);
            int ap = (int) a.getOrDefault("points", 0), bp = (int) b.getOrDefault("points", 0);
            if (ap != bp) return Integer.compare(bp, ap);
            int an = (int) a.getOrDefault("net", 0), bn = (int) b.getOrDefault("net", 0);
            if (an != bn) return Integer.compare(bn, an);
            int at = (int) a.getOrDefault("totalScore", 0), bt = (int) b.getOrDefault("totalScore", 0);
            if (at != bt) return Integer.compare(bt, at);
            int a2 = (int) a.getOrDefault("matchGe2Count", 0), b2 = (int) b.getOrDefault("matchGe2Count", 0);
            if (a2 != b2) return Integer.compare(b2, a2);
            int am = (int) a.getOrDefault("matchMaxScore", 0), bm = (int) b.getOrDefault("matchMaxScore", 0);
            if (am != bm) return Integer.compare(bm, am);
            int as = (int) a.getOrDefault("stealCount", 0), bs = (int) b.getOrDefault("stealCount", 0);
            if (as != bs) return Integer.compare(bs, as);
            int ax = (int) a.getOrDefault("stealMax", 0), bx = (int) b.getOrDefault("stealMax", 0);
            if (ax != bx) return Integer.compare(bx, ax);
            return String.valueOf(a.getOrDefault("username", "")).compareTo(String.valueOf(b.getOrDefault("username", "")));
        });
        for (int i = 0; i < rows.size(); i++) rows.get(i).put("overallRank", i + 1);
        return Map.of("tournamentId", tournamentId, "overallRanking", rows);
    }

    @GetMapping("/tournament/{tournamentId}/group/{groupId}/ranking")
    public Map<String, Object> getTournamentOneGroupRanking(@PathVariable Long tournamentId, @PathVariable Long groupId) {
        Map<String, Object> data = getTournamentGroupRanking(tournamentId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) data.getOrDefault("groups", List.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pseudoGroups = (List<Map<String, Object>>) data.getOrDefault("pseudoGroups", List.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groupMatches = (List<Map<String, Object>>) data.getOrDefault("groupMatches", List.of());
        Map<String, Object> group = groups.stream().filter(g -> Objects.equals(g.get("groupId"), groupId)).findFirst().orElse(null);
        if (group == null) {
            return Map.of("tournamentId", tournamentId, "groupId", groupId, "groupName", "-", "ranking", List.of(), "pseudoGroups", List.of(), "matches", List.of());
        }
        String groupName = String.valueOf(group.getOrDefault("groupName", "-"));
        List<Map<String, Object>> thisPseudo = pseudoGroups.stream()
                .filter(pg -> Objects.equals(String.valueOf(pg.getOrDefault("fromGroup", "")), groupName))
                .toList();
        List<Map<String, Object>> thisMatches = groupMatches.stream()
                .filter(m -> Objects.equals(m.get("groupId"), groupId))
                .toList();
        return Map.of(
                "tournamentId", tournamentId,
                "groupId", groupId,
                "groupName", groupName,
                "ranking", group.getOrDefault("ranking", List.of()),
                "pseudoGroups", thisPseudo,
                "matches", thisMatches
        );
    }

    private static List<Map<String, Object>> buildSetRowsForExport(List<SetScore> scores, Match match, Map<Long, String> usernameById) {
        if (scores == null || scores.isEmpty() || match == null) return List.of();
        return scores.stream().map(s -> {
            String p1 = Boolean.TRUE.equals(s.getPlayer1IsX()) ? "X" : String.valueOf(s.getPlayer1Score() == null ? 0 : s.getPlayer1Score());
            String p2 = Boolean.TRUE.equals(s.getPlayer2IsX()) ? "X" : String.valueOf(s.getPlayer2Score() == null ? 0 : s.getPlayer2Score());
            String hammer = Objects.equals(s.getHammerPlayerId(), match.getPlayer1Id())
                    ? usernameById.getOrDefault(match.getPlayer1Id(), "待定")
                    : (Objects.equals(s.getHammerPlayerId(), match.getPlayer2Id())
                    ? usernameById.getOrDefault(match.getPlayer2Id(), "待定")
                    : "-");
            return Map.<String, Object>of(
                    "setNumber", s.getSetNumber(),
                    "player1ScoreText", p1,
                    "player2ScoreText", p2,
                    "hammer", hammer
            );
        }).toList();
    }

    /**
     * 总排名：导出单个选手在“最近20个系列”中的战绩（Top10 常规系列 + 决赛/年终系列另外标记）
     */
    @GetMapping("/user/{userId}/total/performance")
    public Map<String, Object> getUserTotalPerformance(@PathVariable Long userId) {
        List<Series> recentSeries = seriesService.lambdaQuery()
                .orderByDesc(Series::getCreatedAt)
                .last("LIMIT 20")
                .list();
        List<Long> seriesIds = recentSeries.stream().map(Series::getId).filter(Objects::nonNull).toList();
        return buildUserPerformance(userId, seriesIds, "总排名（最近20系列）");
    }

    @GetMapping("/username/{username}/total/performance")
    public Map<String, Object> getUserTotalPerformanceByUsername(@PathVariable String username) {
        User u = userService.findByUsername(username);
        Long userId = u != null ? u.getId() : null;

        List<Series> recentSeries = seriesService.lambdaQuery()
                .orderByDesc(Series::getCreatedAt)
                .last("LIMIT 20")
                .list();
        List<Long> seriesIds = recentSeries.stream().map(Series::getId).filter(Objects::nonNull).toList();
        return buildUserPerformance(userId, seriesIds, "总排名（最近20系列）");
    }

    /**
     * 赛季排名：导出单个选手在该赛季中的战绩（Top10 常规系列 + 决赛/年终系列另外标记）
     */
    @GetMapping("/user/{userId}/season/{seasonId}/performance")
    public Map<String, Object> getUserSeasonPerformance(@PathVariable Long userId, @PathVariable Long seasonId) {
        List<Long> seriesIds = seriesService.lambdaQuery()
                .eq(Series::getSeasonId, seasonId)
                .orderByAsc(Series::getSequence)
                .list()
                .stream()
                .map(Series::getId)
                .filter(Objects::nonNull)
                .toList();
        Season season = seasonService.getById(seasonId);
        String seasonLabel = season == null ? ("赛季" + seasonId) : (season.getYear() + "年" + (season.getHalf() == 1 ? "上半年" : "下半年"));
        return buildUserPerformance(userId, seriesIds, "赛季排名（" + seasonLabel + "）");
    }

    @GetMapping("/username/{username}/season/{seasonId}/performance")
    public Map<String, Object> getUserSeasonPerformanceByUsername(@PathVariable String username, @PathVariable Long seasonId) {
        User u = userService.findByUsername(username);
        Long userId = u != null ? u.getId() : null;

        List<Long> seriesIds = seriesService.lambdaQuery()
                .eq(Series::getSeasonId, seasonId)
                .orderByAsc(Series::getSequence)
                .list()
                .stream()
                .map(Series::getId)
                .filter(Objects::nonNull)
                .toList();

        Season season = seasonService.getById(seasonId);
        String seasonLabel = season == null ? ("赛季" + seasonId) : (season.getYear() + "年" + (season.getHalf() == 1 ? "上半年" : "下半年"));
        return buildUserPerformance(userId, seriesIds, "赛季排名（" + seasonLabel + "）");
    }

    private Map<String, Object> buildUserPerformance(Long userId, List<Long> seriesIds, String title) {
        User user = userId == null ? null : userService.getById(userId);
        String username = user != null ? user.getUsername() : "未知";
        if (seriesIds == null) seriesIds = List.of();

        // 找出“总决赛/年终总决赛所在系列”
        List<Long> finalsSeriesIds = seriesIds.isEmpty()
                ? List.of()
                : rankingMapper.selectSeriesIdsHavingLevels(seriesIds, List.of("CM Fi-A", "CM Fi-B", "CM An-Fi"));

        // 常规系列 max points（排除决赛系列）
        List<Map<String, Object>> seriesRows = seriesIds.isEmpty()
                ? List.of()
                : rankingMapper.selectUserSeriesMaxPointsForUserExcludingSeriesIds(userId, seriesIds, finalsSeriesIds);
        Map<Long, Integer> seriesPointsById = new HashMap<>();
        for (Map<String, Object> row : seriesRows) {
            Long sid = toLong(row.get("seriesId"));
            Integer pts = toInt(row.get("seriesPoints"));
            if (sid == null || pts == null) continue;
            seriesPointsById.put(sid, pts);
        }

        // seriesId -> meta
        Map<Long, Series> seriesById = seriesIds.isEmpty()
                ? Map.of()
                : seriesService.listByIds(seriesIds).stream().collect(Collectors.toMap(Series::getId, s -> s, (a, b) -> a));
        Set<Long> seasonIds = seriesById.values().stream().map(Series::getSeasonId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Season> seasonById = seasonIds.isEmpty()
                ? Map.of()
                : seasonService.listByIds(new ArrayList<>(seasonIds)).stream().collect(Collectors.toMap(Season::getId, s -> s, (a, b) -> a));

        // 常规系列列表（仅取最近20范围内的 seriesId），按积分降序
        List<UserPerformanceSeriesItemDto> regular = new ArrayList<>();
        for (Long sid : seriesIds) {
            if (finalsSeriesIds.contains(sid)) continue;
            Integer pts = seriesPointsById.get(sid);
            if (pts == null) continue;
            Series s = seriesById.get(sid);
            Season se = s != null ? seasonById.get(s.getSeasonId()) : null;
            String seasonLabel = se == null ? "" : (se.getYear() + "年" + (se.getHalf() == 1 ? "上半年" : "下半年"));
            String seriesName = s == null ? "" : buildSeriesDisplayName(s.getSeasonId(), s);
            regular.add(new UserPerformanceSeriesItemDto(seasonLabel, seriesName, s != null ? s.getSequence() : null, pts, false));
        }
        regular.sort(Comparator.comparingInt((UserPerformanceSeriesItemDto r) -> r.getPoints() != null ? r.getPoints() : 0).reversed()
                .thenComparing(r -> r.getSeriesSequence() != null ? r.getSeriesSequence() : 9999));
        for (int i = 0; i < Math.min(10, regular.size()); i++) {
            regular.get(i).setCountedInTop10(true);
        }

        // 决赛/年终：按 series + level 分组（同系列同等级可能多个赛事，这里按积分总和）
        List<Map<String, Object>> finalsRows = seriesIds.isEmpty()
                ? List.of()
                : rankingMapper.selectUserFinalsPointsBySeriesAndLevel(userId, seriesIds, List.of("CM Fi-A", "CM Fi-B", "CM An-Fi"));
        List<UserPerformanceFinalItemDto> finals = new ArrayList<>();
        Map<String, String> levelNameByCode = tournamentLevelService.list().stream()
                .collect(Collectors.toMap(TournamentLevel::getCode, TournamentLevel::getName, (a, b) -> a));
        for (Map<String, Object> row : finalsRows) {
            Long sid = toLong(row.get("seriesId"));
            String levelCode = row.get("levelCode") != null ? row.get("levelCode").toString() : "";
            Integer pts = toInt(row.get("totalPoints"));
            if (sid == null || pts == null) continue;
            Series s = seriesById.get(sid);
            Season se = s != null ? seasonById.get(s.getSeasonId()) : null;
            String seasonLabel = se == null ? "" : (se.getYear() + "年" + (se.getHalf() == 1 ? "上半年" : "下半年"));
            String seriesName = s == null ? "" : buildSeriesDisplayName(s.getSeasonId(), s);
            String levelName = levelNameByCode.getOrDefault(levelCode, levelCode);
            finals.add(new UserPerformanceFinalItemDto(seasonLabel, seriesName, levelName, pts));
        }

        int regularTop10Sum = regular.stream().filter(UserPerformanceSeriesItemDto::isCountedInTop10).mapToInt(r -> r.getPoints() != null ? r.getPoints() : 0).sum();
        int finalsMax = finals.stream().mapToInt(f -> f.getPoints() != null ? f.getPoints() : 0).max().orElse(0);

        return Map.of(
                "title", title,
                "userId", userId,
                "username", username,
                "regularTop10Sum", regularTop10Sum,
                "finalsSum", finalsMax,
                "totalPoints", regularTop10Sum + finalsMax,
                "regularSeries", regular,
                "finals", finals
        );
    }

    private static String toTournamentStatusLabel(Integer status) {
        if (status == null) return "-";
        return switch (status) {
            case 0 -> "筹备中";
            case 1 -> "进行中";
            case 2 -> "已结束";
            default -> "-";
        };
    }

    private String buildSeriesLabel(Season season, Series series) {
        String seasonText = season == null ? "" : (season.getYear() + "年" + (season.getHalf() == 1 ? "上半年" : "下半年"));
        String displayName = buildSeriesDisplayName(series.getSeasonId(), series);
        return seasonText + " - " + displayName;
    }

    /**
     * 系列展示名称规则：
     * - 若 name 有值：直接显示 name
     * - 若 name 为空：显示为“第{sequence - namedCount}系列”，其中 namedCount 为同赛季、sequence<=当前 且 name 非空的系列数
     */
    private String buildSeriesDisplayName(Long seasonId, Series series) {
        if (series == null) return "";
        if (series.getName() != null && !series.getName().trim().isEmpty()) {
            return series.getName().trim();
        }
        Integer seq = series.getSequence();
        if (seasonId == null || seq == null) {
            return "第" + (seq != null ? seq : "?") + "系列";
        }
        long namedCount = seriesService.lambdaQuery()
                .eq(Series::getSeasonId, seasonId)
                .le(Series::getSequence, seq)
                .list()
                .stream()
                .filter(s -> s.getName() != null && !s.getName().trim().isEmpty())
                .count();
        int idx = (int) (seq - namedCount);
        if (idx < 1) idx = 1;
        return "第" + idx + "系列";
    }

    private static Integer parseLimit(String limit) {
        if (limit == null) return null;
        String t = limit.trim();
        if (t.isEmpty()) return null;
        if ("all".equalsIgnoreCase(t)) return null;
        if ("24".equalsIgnoreCase(t)) return 24;
        try {
            int v = Integer.parseInt(t);
            return v > 0 ? v : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int sanitizeTopN(Integer topN) {
        int v = (topN == null) ? 24 : topN;
        if (v < 1) return 1;
        return Math.min(v, 200);
    }

    private List<RankingListEntryDto> toRankedList(List<RankingEntry> entries) {
        if (entries == null || entries.isEmpty()) return List.of();

        // 兜底：有些排名查询可能只拿到 username 而 userId 为空，
        // 复制战绩功能依赖 userId（用于调用 /ranking/api/user/{userId}/...）。
        Set<String> needResolve = entries.stream()
                .filter(e -> e != null && e.getUserId() == null)
                .map(RankingEntry::getUsername)
                .filter(u -> u != null && !u.trim().isEmpty())
                .collect(Collectors.toSet());

        Map<String, Long> userIdByUsername = new HashMap<>();
        for (String uname : needResolve) {
            User u = userService.findByUsername(uname);
            if (u != null && u.getId() != null) {
                userIdByUsername.put(uname, u.getId());
            }
        }

        List<RankingListEntryDto> result = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            RankingEntry e = entries.get(i);
            String username = e.getUsername();
            Long userId = e.getUserId();
            if (userId == null && username != null) {
                userId = userIdByUsername.get(username);
            }
            result.add(new RankingListEntryDto(i + 1, userId, username, e.getPoints()));
        }
        return result;
    }

    /**
     * 计算“届次”：同赛季 + 同赛事等级内，第 N 届（按创建时间升序）
     * 复用 tournament-detail 的逻辑，但这里一次性计算当前系列所有赛事的 edition。
     */
    private Map<Long, Integer> computeEditionByTournamentId(Long seasonId, List<Tournament> tournaments) {
        Map<Long, Integer> editionByTournamentId = new HashMap<>();
        if (seasonId == null || tournaments == null || tournaments.isEmpty()) return editionByTournamentId;

        Set<String> levelCodes = tournaments.stream()
                .map(Tournament::getLevelCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (levelCodes.isEmpty()) return editionByTournamentId;

        List<Long> seasonSeriesIds = seriesService.lambdaQuery()
                .eq(Series::getSeasonId, seasonId)
                .list()
                .stream()
                .filter(s -> !isTestSeries(s))
                .map(Series::getId)
                .filter(Objects::nonNull)
                .toList();

        if (seasonSeriesIds.isEmpty()) return editionByTournamentId;

        for (String levelCode : levelCodes) {
            List<Tournament> sameGroup = tournamentService.lambdaQuery()
                    .in(Tournament::getSeriesId, seasonSeriesIds)
                    .eq(Tournament::getLevelCode, levelCode)
                    .list();

            sameGroup.sort(Comparator.comparing(Tournament::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder())));

            for (int i = 0; i < sameGroup.size(); i++) {
                Tournament tt = sameGroup.get(i);
                if (tt.getId() != null) {
                    editionByTournamentId.put(tt.getId(), i + 1);
                }
            }
        }

        return editionByTournamentId;
    }

    private static boolean isTestSeries(Series series) {
        if (series == null) return false;
        String name = series.getName();
        return name != null && name.contains("测试");
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

