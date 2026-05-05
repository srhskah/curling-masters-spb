package com.example.dto;

import java.util.List;

/**
 * 与首页奖牌榜一致：某赛事是否可按“最终积分排名”稳定产出前三名（金/银/铜）。
 */
public record MedalTop3Resolution(boolean eligible, List<Long> top3, String reason) {
    public static MedalTop3Resolution ok(List<Long> top3) {
        return new MedalTop3Resolution(true, top3 == null ? List.of() : top3, null);
    }

    public static MedalTop3Resolution skip(String reason) {
        return new MedalTop3Resolution(false, List.of(), reason);
    }
}
