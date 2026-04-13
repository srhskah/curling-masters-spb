package com.example.service.knockout;

import java.util.ArrayList;
import java.util.List;

/** 淘汰赛首轮配对工具。classicOverallRankPairs：名次为小组赛总排名 1…n，与小组个数、奇偶无关。 */
public final class KnockoutPairingUtil {

    private KnockoutPairingUtil() {}

    public static List<int[]> classicOverallRankPairs(int bracketSize) {
        if (bracketSize != 2 && bracketSize != 4 && bracketSize != 8 && bracketSize != 16 && bracketSize != 32) {
            throw new IllegalArgumentException("淘汰赛首轮仅支持 32/16/8/4/2 人规模");
        }
        List<Integer> seeds = new ArrayList<>();
        seeds.add(1);
        seeds.add(2);
        while (seeds.size() < bracketSize) {
            int next = seeds.size() * 2;
            List<Integer> expanded = new ArrayList<>(next);
            for (int s : seeds) {
                expanded.add(s);
                expanded.add(next + 1 - s);
            }
            seeds = expanded;
        }
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < seeds.size(); i += 2) {
            out.add(new int[]{seeds.get(i), seeds.get(i + 1)});
        }
        return out;
    }

    public static List<int[]> halfCrossPairsBetweenTwoGroups(int size) {
        if (size < 2 || size % 2 != 0) {
            throw new IllegalArgumentException("组间交叉要求每组晋级偶数人");
        }
        List<int[]> out = new ArrayList<>();
        // 每组晋级 size 人，两个组交叉应产出 size 场（覆盖两组全部晋级者）
        // 为了让强种子与挂载资格赛占位分布更平衡，顺序采用：
        // size=2: A1-B2, A2-B1
        // size=4: A1-B4, A3-B2, A2-B3, A4-B1
        for (int i = 0; i < size; i++) {
            int aRank;
            if (i == 0) {
                aRank = 1;
            } else if (i == size - 1) {
                aRank = size;
            } else if (i % 2 == 1) {
                aRank = size - (i / 2) - 1;
            } else {
                aRank = (i / 2) + 1;
            }
            int bRank = size + 1 - aRank;
            out.add(new int[]{aRank, bRank});
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
