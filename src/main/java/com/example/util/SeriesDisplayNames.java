package com.example.util;

import com.example.entity.Season;
import com.example.entity.Series;
import com.example.service.SeriesService;

import java.util.Objects;

/**
 * 赛事系列在页面/API/PDF 中的展示名称（与 {@code TournamentController}、{@code RankingApiController} 原规则一致）。
 * <ul>
 *   <li>若 {@link Series#getName()} 非空：使用该名称</li>
 *   <li>否则：同赛季内按 sequence 顺序，排除已命名系列后得到「第 N 系列」</li>
 * </ul>
 */
public final class SeriesDisplayNames {

    private SeriesDisplayNames() {
    }

    /**
     * 系列单独展示名（不含赛季前缀）。
     */
    public static String seriesDisplayName(SeriesService seriesService, Series series) {
        if (series == null) {
            return "";
        }
        if (series.getName() != null && !series.getName().trim().isEmpty()) {
            return series.getName().trim();
        }
        Long seasonId = series.getSeasonId();
        Integer seq = series.getSequence();
        if (seasonId == null || seq == null) {
            return "第" + (seq != null ? seq : "?") + "系列";
        }
        if (seriesService == null) {
            return "第" + seq + "系列";
        }
        long namedCount = seriesService.lambdaQuery()
                .eq(Series::getSeasonId, seasonId)
                .le(Series::getSequence, seq)
                .list()
                .stream()
                .filter(s -> s != null && s.getName() != null && !s.getName().trim().isEmpty())
                .count();
        int idx = (int) (seq - namedCount);
        if (idx < 1) {
            idx = 1;
        }
        return "第" + idx + "系列";
    }

    /**
     * 「赛季标签 · 系列展示名」，与排名 API 中 seriesLabel 风格一致。
     */
    public static String seasonAndSeriesLine(Season season, Series series, SeriesService seriesService) {
        String seasonText = formatSeasonShort(season);
        String sn = seriesDisplayName(seriesService, series);
        if (seasonText.isEmpty()) {
            return sn;
        }
        if (sn.isEmpty()) {
            return seasonText;
        }
        return seasonText + " · " + sn;
    }

    public static String formatSeasonShort(Season season) {
        if (season == null) {
            return "";
        }
        return season.getYear() + "年" + (Objects.equals(season.getHalf(), 1) ? "上半年" : "下半年");
    }

    /** 同 {@link #seriesDisplayName}，供控制器历史调用名兼容 */
    public static String buildSeriesDisplayName(SeriesService seriesService, Series series) {
        return seriesDisplayName(seriesService, series);
    }

    /** 同 {@link #formatSeasonShort} */
    public static String formatSeason(Season season) {
        return formatSeasonShort(season);
    }
}
