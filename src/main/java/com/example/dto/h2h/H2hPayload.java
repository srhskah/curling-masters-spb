package com.example.dto.h2h;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 显式 JSON 属性名，避免部分 Jackson/序列化配置下 seasons 缺失导致前端读不到数据。
 *
 * @param soloStats 仅传 userId1、且 userId2 为空时：选手一在返回场次中的聚合统计
 * @param h2hStatsUser1VsUser2 双方 H2H 时：userId1 对 userId2 视角（胜=选手一胜该场）
 * @param h2hStatsUser2VsUser1 双方 H2H 时：userId2 对 userId1 视角（便于前端交换展示，无需再次请求）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record H2hPayload(
        @JsonProperty("seasons") List<H2hSeasonNode> seasons,
        @JsonProperty("queryUserId1") String queryUserId1,
        @JsonProperty("queryUserId2") String queryUserId2,
        @JsonProperty("soloStats") H2hPlayerExportStats soloStats,
        @JsonProperty("h2hStatsUser1VsUser2") H2hPlayerExportStats h2hStatsUser1VsUser2,
        @JsonProperty("h2hStatsUser2VsUser1") H2hPlayerExportStats h2hStatsUser2VsUser1
) {
    public static H2hPayload empty() {
        return new H2hPayload(List.of(), null, null, null, null, null);
    }
}
