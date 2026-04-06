package com.example.service.knockout;

import java.util.ArrayList;
import java.util.List;

/** 淘汰赛首轮配对工具。classicOverallRankPairs：名次为小组赛总排名 1…n，与小组个数、奇偶无关。 */
public final class KnockoutPairingUtil {

    private KnockoutPairingUtil() {}

    public static List<int[]> classicOverallRankPairs(int bracketSize) {
        if (bracketSize != 2 && bracketSize != 4 && bracketSize != 8 && bracketSize != 16) {
            throw new IllegalArgumentException("淘汰赛首轮仅支持 16/8/4/2 人规模");
        }
        return switch (bracketSize) {
            case 2 -> List.of(new int[]{1, 2});
            case 4 -> List.of(new int[]{1, 4}, new int[]{2, 3});
            case 8 -> List.of(new int[]{1, 8}, new int[]{4, 5}, new int[]{3, 6}, new int[]{2, 7});
            case 16 -> List.of(
                    new int[]{1, 16}, new int[]{8, 9}, new int[]{5, 12}, new int[]{4, 13},
                    new int[]{3, 14}, new int[]{6, 11}, new int[]{7, 10}, new int[]{2, 15});
            default -> List.of();
        };
    }

    public static List<int[]> halfCrossPairsBetweenTwoGroups(int size) {
        if (size < 2 || size % 2 != 0) {
            throw new IllegalArgumentException("组间交叉要求每组晋级偶数人");
        }
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < size / 2; i++) {
            out.add(new int[]{i + 1, size - i});
        }
        return out;
    }

    /**
     * 世界杯式：相邻两组之间第 t 条对阵（名次 1…q），t 从 0 起，配对为 (t+1, q-t)。
     */
    public static int[] worldCupCrossRankPair(int t, int q) {
        if (q < 1 || t < 0 || t >= q) {
            throw new IllegalArgumentException("世界杯式名次配对参数无效");
        }
        return new int[]{t + 1, q - t};
    }

    /**
     * 兼容旧逻辑：等价于 2 个组对、每条名次线交错时的展开序列（仅用于测试或对照）。
     */
    public static List<int[]> worldCupUpperFourGroupsPairs(int ranksPerGroup) {
        if (ranksPerGroup < 1) {
            throw new IllegalArgumentException("每组晋级人数无效");
        }
        List<int[]> out = new ArrayList<>();
        for (int t = 0; t < ranksPerGroup; t++) {
            int[] pr = worldCupCrossRankPair(t, ranksPerGroup);
            out.add(pr);
            out.add(pr);
        }
        return out;
    }

    public static List<int[]> worldCupLowerFourGroupsPairs(int ranksPerGroup) {
        List<int[]> out = new ArrayList<>();
        for (int t = 0; t < ranksPerGroup; t++) {
            int[] pr = worldCupCrossRankPair(t, ranksPerGroup);
            out.add(new int[]{pr[1], pr[0]});
            out.add(new int[]{pr[1], pr[0]});
        }
        return out;
    }
}
