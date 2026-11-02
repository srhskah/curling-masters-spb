package com.example.service.knockout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 对阵图视觉半区：优先用赛事写入的 {@code knockoutHalf}（0 上 / 1 下），
 * 无半区标记时再按该轮场次数对半切分卡位。
 */
public final class KnockoutBracketLayout {

    private KnockoutBracketLayout() {}

    public static boolean isUpper(Integer knockoutHalf, Integer bracketSlot, int roundMatchCount) {
        if (knockoutHalf != null) {
            return knockoutHalf == 0;
        }
        Integer inferred = inferHalfFromFirstRoundSplit(bracketSlot, roundMatchCount);
        if (inferred == null) {
            return true;
        }
        return inferred == 0;
    }

    /**
     * 经典种子交叉：上下半区由该轮场次对半切开（卡位 0..(n/2-1) 上半区，其余下半区）。
     * 场次不足 2 时无法分半，返回 null。
     */
    public static Integer inferHalfFromFirstRoundSplit(Integer bracketSlot, int roundMatchCount) {
        if (bracketSlot == null || roundMatchCount <= 1) {
            return null;
        }
        int cut = Math.max(1, roundMatchCount / 2);
        return bracketSlot < cut ? 0 : 1;
    }

    /**
     * 将一场轮次排成左侧（上半区）+ 右侧（下半区）的视觉槽位，长度为 {@code expectedSize}。
     */
    public static <T> List<T> placeVisualSlots(List<T> source,
                                               int expectedSize,
                                               Function<T, Integer> halfFn,
                                               Function<T, Integer> slotFn) {
        if (expectedSize <= 0) {
            return List.of();
        }
        List<T> list = source == null ? List.of() : source.stream().filter(Objects::nonNull).toList();
        List<T> arr = new ArrayList<>(Collections.nCopies(expectedSize, null));
        boolean anyHalf = list.stream().anyMatch(x -> halfFn.apply(x) != null);
        if (!anyHalf) {
            for (T x : list) {
                Integer slot = slotFn.apply(x);
                int idx = slot == null ? -1 : slot;
                if (idx >= 0 && idx < expectedSize && arr.get(idx) == null) {
                    arr.set(idx, x);
                }
            }
            int write = 0;
            for (T x : list) {
                Integer slot = slotFn.apply(x);
                int idx = slot == null ? -1 : slot;
                if (idx >= 0 && idx < expectedSize) {
                    continue;
                }
                while (write < expectedSize && arr.get(write) != null) {
                    write++;
                }
                if (write < expectedSize) {
                    arr.set(write, x);
                    write++;
                }
            }
            return arr;
        }
        int cut = Math.max(1, expectedSize / 2);
        List<T> upper = new ArrayList<>();
        List<T> lower = new ArrayList<>();
        for (T x : list) {
            if (isUpper(halfFn.apply(x), slotFn.apply(x), expectedSize)) {
                upper.add(x);
            } else {
                lower.add(x);
            }
        }
        fillRange(arr, 0, cut, upper);
        fillRange(arr, cut, expectedSize, lower);
        List<T> leftover = new ArrayList<>();
        if (upper.size() > cut) {
            leftover.addAll(upper.subList(cut, upper.size()));
        }
        int lowerCap = expectedSize - cut;
        if (lower.size() > lowerCap) {
            leftover.addAll(lower.subList(lowerCap, lower.size()));
        }
        int write = 0;
        for (T x : leftover) {
            while (write < expectedSize && arr.get(write) != null) {
                write++;
            }
            if (write >= expectedSize) {
                break;
            }
            arr.set(write, x);
            write++;
        }
        return arr;
    }

    private static <T> void fillRange(List<T> arr, int from, int to, List<T> items) {
        int n = Math.min(items.size(), Math.max(0, to - from));
        for (int i = 0; i < n; i++) {
            arr.set(from + i, items.get(i));
        }
    }
}
