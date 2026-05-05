package com.example.util;

import com.example.entity.UserTournamentPoints;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 赛事积分榜展示顺序：与 {@code getProgressSettledPlacementRanks} 一致——未定名次（仍在争冠）靠前按积分，
 * 已定名次按数字升序（1 最好）；退赛行（无 userId）垫底。
 */
public final class TournamentPlacementListOrder {

    private static final Comparator<UserTournamentPoints> BY_POINTS_DESC_UID = Comparator
            .comparing(UserTournamentPoints::getPoints, Comparator.nullsLast(Comparator.naturalOrder())).reversed()
            .thenComparing(UserTournamentPoints::getUserId, Comparator.nullsLast(Long::compareTo));

    private TournamentPlacementListOrder() {
    }

    public static void sortUtpsByPlacementThenPoints(List<UserTournamentPoints> utps, Map<Long, Integer> placements) {
        if (utps == null || utps.size() <= 1) {
            return;
        }
        Map<Long, Integer> pl = placements == null ? Map.of() : placements;
        utps.sort((a, b) -> compareUtps(a, b, pl));
    }

    public static int compareUtps(UserTournamentPoints a, UserTournamentPoints b, Map<Long, Integer> placements) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        Long ua = a.getUserId();
        Long ub = b.getUserId();
        if (ua == null && ub == null) {
            return 0;
        }
        if (ua == null) {
            return 1;
        }
        if (ub == null) {
            return -1;
        }
        Integer pa = placements.get(ua);
        Integer pb = placements.get(ub);
        boolean aSet = pa != null;
        boolean bSet = pb != null;
        if (!aSet && !bSet) {
            return BY_POINTS_DESC_UID.compare(a, b);
        }
        if (!aSet && bSet) {
            return -1;
        }
        if (aSet && !bSet) {
            return 1;
        }
        int c = Integer.compare(pa, pb);
        if (c != 0) {
            return c;
        }
        return BY_POINTS_DESC_UID.compare(a, b);
    }

    /**
     * API / 导出用：已定名次为 1…N；未定名次或退赛行为 0（前端结合 {@code withdrawn} 显示「—」）。
     */
    public static int rowRankForApi(UserTournamentPoints utp, Map<Long, Integer> placements) {
        if (utp == null || utp.getUserId() == null) {
            return 0;
        }
        Integer p = placements == null ? null : placements.get(utp.getUserId());
        return p != null ? p : 0;
    }
}
