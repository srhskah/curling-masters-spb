package com.example.dto.h2h;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * H2H 导出用：从某一名选手的视角，在指定场次集合上的聚合统计。
 */
public record H2hPlayerExportStats(
        @JsonProperty("wins") int wins,
        @JsonProperty("draws") int draws,
        @JsonProperty("losses") int losses,
        @JsonProperty("totalFor") int totalFor,
        @JsonProperty("totalAgainst") int totalAgainst,
        @JsonProperty("maxSingleEndScore") int maxSingleEndScore,
        @JsonProperty("stealEnds") int stealEnds,
        @JsonProperty("maxStealSingleEnd") int maxStealSingleEnd
) {
    /** 无数据或统计缺失时的占位 */
    public static H2hPlayerExportStats empty() {
        return new H2hPlayerExportStats(0, 0, 0, 0, 0, 0, 0, 0);
    }
}
