package com.example.util;

import com.example.entity.UserTournamentPoints;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 赛事积分榜展示顺序：
 * <ul>
 *   <li>默认：与 {@code getProgressSettledPlacementRanks} 一致——未定名次（仍在争冠）靠前按积分，
 *       已定名次按数字升序（1 最好）；退赛行（无 userId）垫底。</li>
 *   <li>若本赛事存在已生效取消资格：取消资格选手积分为 0 后，全体按<strong>积分降序</strong>重排，
 *       名次为连续 1…N（{@link #sortAndResolveDisplayRanks}）。</li>
 * </ul>
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

    /**
     * 有取消资格时：先按积分重排列表，再生成与列表顺序一致的连续名次（供 {@link #rowRankForApi}）。
     * 无取消资格：按进程名次规则排序，返回 {@code progressPlacements}（可能为空 map，勿修改共享引用时需注意）。
     */
    public static Map<Long, Integer> sortAndResolveDisplayRanks(
            List<UserTournamentPoints> utps,
            Map<Long, Integer> progressPlacements,
            boolean tournamentHasEffectiveDisqualification) {
        if (utps == null) {
            return Map.of();
        }
        if (tournamentHasEffectiveDisqualification) {
            sortUtpsByPointsDescWithdrawnLast(utps);
            return sequentialRanksFromCurrentUtpsOrder(utps);
        }
        Map<Long, Integer> pl = progressPlacements == null ? Map.of() : progressPlacements;
        sortUtpsByPlacementThenPoints(utps, pl);
        return pl;
    }

    /** userId 非空：积分降序，同分按 userId 升序；userId 为空（退赛占位）固定在最后。 */
    public static void sortUtpsByPointsDescWithdrawnLast(List<UserTournamentPoints> utps) {
        if (utps == null || utps.size() <= 1) {
            return;
        }
        utps.sort((a, b) -> {
            boolean aW = a != null && a.getUserId() == null;
            boolean bW = b != null && b.getUserId() == null;
            if (aW != bW) {
                return aW ? 1 : -1;
            }
            int pa = a != null && a.getPoints() != null ? a.getPoints() : 0;
            int pb = b != null && b.getPoints() != null ? b.getPoints() : 0;
            if (pa != pb) {
                return Integer.compare(pb, pa);
            }
            long ua = a != null && a.getUserId() != null ? a.getUserId() : 0L;
            long ub = b != null && b.getUserId() != null ? b.getUserId() : 0L;
            return Long.compare(ua, ub);
        });
    }

    /** 按当前列表顺序为每条有 userId 的记录赋予名次 1、2、3…（退赛行跳过）。 */
    public static Map<Long, Integer> sequentialRanksFromCurrentUtpsOrder(List<UserTournamentPoints> utps) {
        if (utps == null || utps.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> out = new LinkedHashMap<>();
        int r = 1;
        for (UserTournamentPoints u : utps) {
            if (u == null || u.getUserId() == null) {
                continue;
            }
            out.put(u.getUserId(), r++);
        }
        return out;
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
