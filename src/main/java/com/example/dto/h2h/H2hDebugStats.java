package com.example.dto.h2h;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * H2H 调试统计（仅超级管理员接口返回）。
 * <ul>
 *   <li>{@code acceptanceMatchIdsCount}：仅选手一时为该用户在 {@code match_acceptance} 中出现的<strong>不同 match_id</strong> 数量；
 *       双方 H2H 时为两名用户各自 acceptance match_id 集合的<strong>交集大小</strong>。</li>
 *   <li>{@code acceptanceOnlyAddedCount}：正式 H2H 列表仅槽位查询时恒为 0；保留字段供对照历史/调试含义（曾含验收兜底时非 0）。</li>
 * </ul>
 */
public record H2hDebugStats(
        @JsonProperty("userId1") @JsonFormat(shape = JsonFormat.Shape.STRING) Long userId1,
        @JsonProperty("userId2") @JsonFormat(shape = JsonFormat.Shape.STRING) Long userId2,
        @JsonProperty("queryMode") String queryMode,
        @JsonProperty("directMatchCount") int directMatchCount,
        @JsonProperty("acceptanceMatchIdsCount") int acceptanceMatchIdsCount,
        @JsonProperty("acceptanceOnlyAddedCount") int acceptanceOnlyAddedCount,
        @JsonProperty("mergedUniqueMatchCount") int mergedUniqueMatchCount,
        @JsonProperty("payloadSeasonCount") int payloadSeasonCount,
        @JsonProperty("payloadTotalMatches") int payloadTotalMatches
) {
}
