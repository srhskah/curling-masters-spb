package com.example.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 小组赛「每组人数」布局：
 * <ul>
 *   <li>常规：m 能被 g 整除 → 每组 g 人。</li>
 *   <li>质数 m（&gt;2）：允许 g 为某个 m±n（n &lt; m/2）的因数，并由末组调整人数使总和为 m；
 *       且每组人数 ∈ [3, (m+1)/2]，末组与其他组人数差 ≤ 1。</li>
 * </ul>
 */
public final class GroupSizeLayout {

    private GroupSizeLayout() {
    }

    /**
     * @param groupSize  配置的「每组人数」（前若干组的目标人数）
     * @param groupCount 小组数
     * @param sizes      各组实际容量（末组可能 ≠ groupSize）
     * @param adjustN    相对 m 的偏移；精确整除时为 0
     * @param paddedTotal 选用的 m±n（或 m）；精确整除时等于 m
     */
    public record Layout(int groupSize, int groupCount, int[] sizes, int adjustN, int paddedTotal) {
        public int capacityAt(int zeroBasedIndex) {
            if (sizes == null || zeroBasedIndex < 0 || zeroBasedIndex >= sizes.length) {
                return groupSize;
            }
            return sizes[zeroBasedIndex];
        }

        public boolean lastGroupAdjusted() {
            return adjustN > 0;
        }

        public int lastGroupSize() {
            return sizes == null || sizes.length == 0 ? groupSize : sizes[sizes.length - 1];
        }
    }

    public static boolean isPrime(int n) {
        if (n <= 1) {
            return false;
        }
        if (n <= 3) {
            return true;
        }
        if (n % 2 == 0 || n % 3 == 0) {
            return false;
        }
        for (int i = 5; i * (long) i <= n; i += 6) {
            if (n % i == 0 || n % (i + 2) == 0) {
                return false;
            }
        }
        return true;
    }

    /** 质数参赛人数时：每组 ∈ [3, (m+1)/2]，且末组与其他组人数差 ≤ 1。 */
    static boolean satisfiesPrimeConstraints(int m, int groupSize, int lastGroupSize) {
        if (m <= 2 || !isPrime(m)) {
            return true;
        }
        int max = (m + 1) / 2;
        if (groupSize < 3 || groupSize > max) {
            return false;
        }
        if (lastGroupSize < 3 || lastGroupSize > max) {
            return false;
        }
        return Math.abs(lastGroupSize - groupSize) <= 1;
    }

    /**
     * 可选每组人数：能与 m 组成合法布局的 g（≥2 且 ≤m）。
     */
    public static List<Integer> calcGroupSizeOptions(Integer participantCount) {
        if (participantCount == null || participantCount < 2) {
            return List.of();
        }
        int m = participantCount;
        List<Integer> options = new ArrayList<>();
        for (int g = 2; g <= m; g++) {
            if (resolve(m, g) != null) {
                options.add(g);
            }
        }
        return options;
    }

    /**
     * 可选小组数（抽签侧）：合法布局的 groupCount，排除 1 组与「每人一组」。
     */
    public static List<Integer> calcGroupCountOptions(Integer participantCount) {
        if (participantCount == null || participantCount < 2) {
            return List.of();
        }
        int m = participantCount;
        java.util.LinkedHashSet<Integer> counts = new java.util.LinkedHashSet<>();
        for (int g = 2; g <= m; g++) {
            Layout layout = resolve(m, g);
            if (layout == null) {
                continue;
            }
            int gc = layout.groupCount();
            if (gc >= 2 && gc < m) {
                counts.add(gc);
            }
        }
        List<Integer> out = new ArrayList<>(counts);
        out.sort(Integer::compareTo);
        return out;
    }

    /**
     * 按小组数反查布局（抽签选「小组数」时用）；非法返回 null。
     */
    public static Layout resolveByGroupCount(Integer participantCount, Integer groupCount) {
        if (participantCount == null || groupCount == null) {
            return null;
        }
        int m = participantCount;
        int gc = groupCount;
        if (m < 2 || gc < 1 || gc > m) {
            return null;
        }
        if (m % gc == 0) {
            int g = m / gc;
            return resolve(m, g);
        }
        if (!isPrime(m) || m <= 2) {
            return null;
        }
        Layout best = null;
        for (int g = 2; g <= m; g++) {
            Layout layout = resolve(m, g);
            if (layout != null && layout.groupCount() == gc && isBetter(layout, best)) {
                best = layout;
            }
        }
        return best;
    }

    /**
     * @return 合法布局；无法分组时返回 null
     */
    public static Layout resolve(Integer participantCount, Integer groupSize) {
        if (participantCount == null || groupSize == null) {
            return null;
        }
        return resolve(participantCount.intValue(), groupSize.intValue());
    }

    public static Layout resolve(int m, int g) {
        if (m < 2 || g < 2 || g > m) {
            return null;
        }
        if (m % g == 0) {
            int gc = m / g;
            int[] sizes = new int[gc];
            Arrays.fill(sizes, g);
            Layout exact = new Layout(g, gc, sizes, 0, m);
            if (isPrime(m) && !satisfiesPrimeConstraints(m, g, g)) {
                return null;
            }
            return exact;
        }
        if (!isPrime(m) || m <= 2) {
            return null;
        }
        // 质数布局：先满足人数上下界，再搜 m±n
        int maxSize = (m + 1) / 2;
        if (g < 3 || g > maxSize) {
            return null;
        }
        // n < m/2 → n ∈ [1, floor((m-1)/2)]
        int maxN = (m - 1) / 2;
        Layout best = null;
        for (int n = 1; n <= maxN; n++) {
            for (int sign = -1; sign <= 1; sign += 2) {
                int T = m + sign * n;
                if (T < 2 || T % g != 0) {
                    continue;
                }
                int gc = T / g;
                if (gc < 1) {
                    continue;
                }
                int last = m - (gc - 1) * g;
                if (!satisfiesPrimeConstraints(m, g, last)) {
                    continue;
                }
                int[] sizes = new int[gc];
                Arrays.fill(sizes, 0, gc - 1, g);
                sizes[gc - 1] = last;
                Layout cand = new Layout(g, gc, sizes, n, T);
                if (isBetter(cand, best)) {
                    best = cand;
                }
            }
        }
        return best;
    }

    /** 更小的 n 优先；同 n 时末组更接近 g 优先；再比小组数更少。 */
    private static boolean isBetter(Layout cand, Layout best) {
        if (best == null) {
            return true;
        }
        if (cand.adjustN != best.adjustN) {
            return cand.adjustN < best.adjustN;
        }
        int candDiff = Math.abs(cand.lastGroupSize() - cand.groupSize);
        int bestDiff = Math.abs(best.lastGroupSize() - best.groupSize);
        if (candDiff != bestDiff) {
            return candDiff < bestDiff;
        }
        return cand.groupCount < best.groupCount;
    }
}
