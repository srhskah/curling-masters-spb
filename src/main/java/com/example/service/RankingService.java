package com.example.service;

import com.example.dto.RankingEntry;
import com.example.entity.Series;

import java.util.List;

public interface RankingService {
    List<RankingEntry> getTotalRanking(Integer limit);
    List<RankingEntry> getSeasonRanking(Long seasonId, Integer limit);
    List<RankingEntry> getSeasonRankingAllSeasonsMerged(Integer limit);

    /**
     * 总排名系列窗口（与 {@link #getTotalRanking} 一致）：
     * <ul>
     *   <li>按「赛季年 → 上下半年 → 系列序号」从新到旧排列</li>
     *   <li>从最新系列往前推，跳过赛季总决赛/年终总决赛所在系列，凑满最多 20 个常规系列</li>
     *   <li>{@code regularSeries}：新→旧；窗口起始为末项</li>
     *   <li>{@code skippedFinalsSeriesIds}：截取过程中跳过的总决赛系列（积分仍计入总排名）</li>
     * </ul>
     */
    TotalRankingSeriesWindow resolveTotalRankingSeriesWindow();

    /**
     * 等同于 {@link #resolveTotalRankingSeriesWindow()}{@code .regularSeries()}。
     */
    default List<Series> listRecentSeriesForTotalRanking() {
        TotalRankingSeriesWindow w = resolveTotalRankingSeriesWindow();
        return w == null || w.regularSeries() == null ? List.of() : w.regularSeries();
    }

    record TotalRankingSeriesWindow(List<Series> regularSeries, List<Long> skippedFinalsSeriesIds) {
        public TotalRankingSeriesWindow {
            regularSeries = regularSeries == null ? List.of() : List.copyOf(regularSeries);
            skippedFinalsSeriesIds = skippedFinalsSeriesIds == null ? List.of() : List.copyOf(skippedFinalsSeriesIds);
        }
    }
}
