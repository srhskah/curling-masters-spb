package com.example.service.impl;

import com.example.dto.MedalTop3Resolution;
import com.example.dto.UserMedalByLevelDto;
import com.example.dto.UserMedalEventDto;
import com.example.dto.UserTournamentPlacementHistoryDto;
import com.example.entity.Season;
import com.example.entity.Series;
import com.example.entity.Tournament;
import com.example.entity.TournamentLevel;
import com.example.entity.UserTournamentPoints;
import com.example.service.ITournamentCompetitionService;
import com.example.service.ITournamentLevelService;
import com.example.service.SeasonService;
import com.example.service.SeriesService;
import com.example.service.TournamentMedalStandingsService;
import com.example.service.TournamentService;
import com.example.service.UserTournamentPointsService;
import com.example.util.SeriesDisplayNames;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 与 {@link com.example.controller.HomeController} 原 {@code resolveTournamentTop3ByFinalRanking} 及奖牌榜累加规则一致。
 */
@Service
public class TournamentMedalStandingsServiceImpl implements TournamentMedalStandingsService {

    @Autowired private TournamentService tournamentService;
    @Autowired private UserTournamentPointsService userTournamentPointsService;
    @Autowired private ITournamentLevelService tournamentLevelService;
    @Autowired private SeriesService seriesService;
    @Autowired private SeasonService seasonService;
    @Autowired private ITournamentCompetitionService tournamentCompetitionService;
    @Autowired private com.example.service.impl.TournamentRankingRosterService tournamentRankingRosterService;

    @Override
    public MedalTop3Resolution resolveTournamentTop3ByFinalRanking(Tournament tournament) {
        if (tournament == null || tournament.getId() == null) {
            return MedalTop3Resolution.skip("invalid_tournament");
        }
        Long tournamentId = tournament.getId();
        List<UserTournamentPoints> rows = userTournamentPointsService.lambdaQuery()
                .eq(UserTournamentPoints::getTournamentId, tournamentId)
                .orderByDesc(UserTournamentPoints::getPoints)
                .orderByAsc(UserTournamentPoints::getId)
                .list();
        if (rows == null || rows.isEmpty()) {
            return MedalTop3Resolution.skip("no_utp_rows");
        }

        int participantCount;
        int pointsUserCount = (int) rows.stream()
                .filter(r -> r != null && r.getUserId() != null)
                .map(UserTournamentPoints::getUserId)
                .distinct()
                .count();
        try {
            Set<Long> roster = tournamentRankingRosterService.rosterUserIdsForEventRanking(tournamentId);
            int rosterCount = roster == null ? 0 : roster.size();
            participantCount = Math.max(rosterCount, pointsUserCount);
        } catch (RuntimeException ignored) {
            participantCount = pointsUserCount;
        }
        List<UserTournamentPoints> valid = rows.stream()
                .filter(r -> r != null && r.getUserId() != null && r.getPoints() != null)
                .toList();
        if (valid.size() < 3) {
            return MedalTop3Resolution.skip("ranked_rows_lt_3");
        }

        Integer championPoints = valid.get(0).getPoints();
        if (championPoints == null) {
            return MedalTop3Resolution.skip("champion_points_null");
        }

        BigDecimal ratio = tournament.getChampionPointsRatio();
        if (ratio == null) {
            TournamentLevel level = tournament.getLevelCode() == null ? null : tournamentLevelService.lambdaQuery()
                    .eq(TournamentLevel::getCode, tournament.getLevelCode())
                    .last("LIMIT 1")
                    .one();
            ratio = level == null ? null : level.getDefaultChampionRatio();
        }
        if (ratio == null) {
            return MedalTop3Resolution.skip("ratio_missing");
        }
        if (ratio.compareTo(BigDecimal.ZERO) <= 0) {
            return MedalTop3Resolution.skip("ratio_invalid");
        }
        try {
            BigDecimal inferredParticipants = BigDecimal.valueOf(championPoints).divide(ratio, 8, RoundingMode.HALF_UP);
            BigDecimal roundedParticipants = inferredParticipants.setScale(0, RoundingMode.HALF_UP);
            if (inferredParticipants.subtract(roundedParticipants).abs().compareTo(new BigDecimal("0.0001")) <= 0) {
                participantCount = Math.max(participantCount, roundedParticipants.intValue());
            }
        } catch (ArithmeticException ignored) {
            // keep participantCount
        }
        if (participantCount < 3) {
            return MedalTop3Resolution.skip("participant_count_lt_3");
        }
        int expectedChampionPoints = ratio.multiply(BigDecimal.valueOf(participantCount))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
        if (!Objects.equals(expectedChampionPoints, championPoints)) {
            return MedalTop3Resolution.skip("champion_points_not_ready");
        }

        List<Long> top3 = new ArrayList<>(3);
        for (UserTournamentPoints row : valid) {
            if (top3.contains(row.getUserId())) {
                continue;
            }
            top3.add(row.getUserId());
            if (top3.size() >= 3) {
                break;
            }
        }
        return top3.size() == 3 ? MedalTop3Resolution.ok(top3) : MedalTop3Resolution.skip("top3_incomplete");
    }

    @Override
    public List<UserMedalByLevelDto> summarizeUserMedalsByLevel(long userId) {
        Map<String, int[]> acc = new HashMap<>();
        for (Tournament t : tournamentService.list()) {
            if (t == null || t.getLevelCode() == null || t.getLevelCode().isBlank()) {
                continue;
            }
            MedalTop3Resolution r = resolveTournamentTop3ByFinalRanking(t);
            if (!r.eligible() || r.top3() == null) {
                continue;
            }
            List<Long> top3 = r.top3();
            for (int i = 0; i < 3 && i < top3.size(); i++) {
                if (Objects.equals(top3.get(i), userId)) {
                    String lc = t.getLevelCode().trim();
                    acc.computeIfAbsent(lc, k -> new int[3])[i]++;
                }
            }
        }
        Map<String, String> labels = tournamentLevelService.list().stream()
                .filter(l -> l != null && l.getCode() != null && !l.getCode().isBlank())
                .collect(Collectors.toMap(TournamentLevel::getCode, l -> {
                    String n = l.getName();
                    return (n == null || n.isBlank()) ? l.getCode() : n.trim();
                }, (a, b) -> a));

        return acc.entrySet().stream()
                .map(e -> {
                    String code = e.getKey();
                    int[] c = e.getValue();
                    return new UserMedalByLevelDto(code, labels.getOrDefault(code, code), c[0], c[1], c[2]);
                })
                .filter(d -> d.total() > 0)
                .sorted(Comparator.comparing(UserMedalByLevelDto::levelLabel))
                .toList();
    }

    @Override
    public List<UserMedalEventDto> listUserMedalEvents(long userId) {
        List<UserMedalEventDto> out = new ArrayList<>();
        for (Tournament t : tournamentService.list()) {
            if (t == null || t.getId() == null) {
                continue;
            }
            MedalTop3Resolution r = resolveTournamentTop3ByFinalRanking(t);
            if (!r.eligible() || r.top3() == null) {
                continue;
            }
            List<Long> top3 = r.top3();
            for (int i = 0; i < top3.size(); i++) {
                if (Objects.equals(top3.get(i), userId)) {
                    out.add(buildMedalEvent(t, i + 1));
                    break;
                }
            }
        }
        out.sort(Comparator.comparing(UserMedalEventDto::tournamentId).reversed());
        return out;
    }

    private UserMedalEventDto buildMedalEvent(Tournament t, int place1based) {
        String levelLabel = labelForLevelCode(t.getLevelCode());
        Series series = t.getSeriesId() == null ? null : seriesService.getById(t.getSeriesId());
        Season season = series == null || series.getSeasonId() == null ? null : seasonService.getById(series.getSeasonId());
        String seasonLabel = SeriesDisplayNames.formatSeasonShort(season);
        String seriesName = SeriesDisplayNames.seriesDisplayName(seriesService, series);
        String medalLabel = place1based == 1 ? "金牌" : (place1based == 2 ? "银牌" : "铜牌");
        String eventTitle = formatTournamentEventTitle(t);
        return new UserMedalEventDto(t.getId(), eventTitle, levelLabel, seasonLabel, seriesName, place1based, medalLabel);
    }

    @Override
    public List<UserTournamentPlacementHistoryDto> buildUserTournamentPlacementHistory(long userId) {
        List<UserTournamentPoints> utps = userTournamentPointsService.lambdaQuery()
                .eq(UserTournamentPoints::getUserId, userId)
                .list();
        if (utps == null || utps.isEmpty()) {
            return List.of();
        }
        List<UserTournamentPlacementHistoryDto> rows = new ArrayList<>();
        for (UserTournamentPoints utp : utps) {
            if (utp == null || utp.getTournamentId() == null) {
                continue;
            }
            Tournament t = tournamentService.getById(utp.getTournamentId());
            if (t == null) {
                continue;
            }
            Integer finalRank = tournamentCompetitionService.getProgressSettledPlacementRank(t.getId(), userId);
            String medalLabel = "";
            MedalTop3Resolution top3 = resolveTournamentTop3ByFinalRanking(t);
            if (top3.eligible() && top3.top3() != null) {
                List<Long> t3 = top3.top3();
                for (int i = 0; i < t3.size(); i++) {
                    if (Objects.equals(t3.get(i), userId)) {
                        medalLabel = i == 0 ? "金牌" : (i == 1 ? "银牌" : "铜牌");
                        break;
                    }
                }
            }
            Series series = t.getSeriesId() == null ? null : seriesService.getById(t.getSeriesId());
            Season season = series == null || series.getSeasonId() == null ? null : seasonService.getById(series.getSeasonId());
            String tname = formatTournamentEventTitle(t);
            rows.add(new UserTournamentPlacementHistoryDto(
                    t.getId(),
                    tname,
                    labelForLevelCode(t.getLevelCode()),
                    SeriesDisplayNames.formatSeasonShort(season),
                    SeriesDisplayNames.seriesDisplayName(seriesService, series),
                    finalRank,
                    utp.getPoints(),
                    medalLabel
            ));
        }
        rows.sort(Comparator
                .comparing((UserTournamentPlacementHistoryDto x) -> x.seasonLabel() == null ? "" : x.seasonLabel())
                .thenComparing(x -> x.tournamentName(), Comparator.nullsLast(Comparator.reverseOrder())));
        return rows;
    }

    /**
     * 赛事在列表中的短标题（级别 · 开赛日）；赛季与系列由 DTO 的 {@code seasonLabel}/{@code seriesName} 单独呈现。
     */
    private String formatTournamentEventTitle(Tournament t) {
        if (t == null) {
            return "";
        }
        String level = labelForLevelCode(t.getLevelCode());
        if (t.getStartDate() != null) {
            return level.isEmpty() ? t.getStartDate().toString() : level + " · " + t.getStartDate();
        }
        return level.isEmpty() ? ("赛事 #" + t.getId()) : level;
    }

    private String labelForLevelCode(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        TournamentLevel l = tournamentLevelService.lambdaQuery().eq(TournamentLevel::getCode, code.trim()).last("LIMIT 1").one();
        if (l == null) {
            return code.trim();
        }
        String n = l.getName();
        return (n == null || n.isBlank()) ? code.trim() : n.trim();
    }

}
