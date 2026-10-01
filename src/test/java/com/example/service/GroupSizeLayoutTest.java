package com.example.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GroupSizeLayoutTest {

    @Test
    void exactDivisorKeepsUniformGroups() {
        GroupSizeLayout.Layout layout = GroupSizeLayout.resolve(12, 3);
        assertNotNull(layout);
        assertEquals(0, layout.adjustN());
        assertEquals(4, layout.groupCount());
        assertArrayEquals(new int[]{3, 3, 3, 3}, layout.sizes());
    }

    @Test
    void primeRequiresGroupSizeBoundsAndNearEqualLast() {
        // m=11：max=(11+1)/2=6；g=3→末组2 非法；g=4→[4,4,3] 合法
        assertNull(GroupSizeLayout.resolve(11, 3));
        assertNull(GroupSizeLayout.resolve(11, 11));

        GroupSizeLayout.Layout layout = GroupSizeLayout.resolve(11, 4);
        assertNotNull(layout);
        assertTrue(layout.adjustN() > 0);
        assertEquals(3, layout.groupCount());
        assertEquals(11, ArraysSum(layout.sizes()));
        assertEquals(3, layout.lastGroupSize());
        assertTrue(Math.abs(layout.lastGroupSize() - layout.groupSize()) <= 1);

        List<Integer> sizeOpts = GroupSizeLayout.calcGroupSizeOptions(11);
        assertTrue(sizeOpts.contains(4));
        assertTrue(sizeOpts.contains(5));
        assertTrue(sizeOpts.contains(6));
        assertFalse(sizeOpts.contains(3));
        assertFalse(sizeOpts.contains(11));

        assertTrue(GroupSizeLayout.calcGroupCountOptions(11).contains(3));
        assertEquals(3, GroupSizeLayout.resolveByGroupCount(11, 3).lastGroupSize());
    }

    @Test
    void primeSevenAllowsThreeAndFour() {
        GroupSizeLayout.Layout a = GroupSizeLayout.resolve(7, 3);
        assertNotNull(a);
        assertArrayEquals(new int[]{3, 4}, a.sizes());

        GroupSizeLayout.Layout b = GroupSizeLayout.resolve(7, 4);
        assertNotNull(b);
        assertArrayEquals(new int[]{4, 3}, b.sizes());
    }

    @Test
    void compositeWithoutExactFactorIsRejected() {
        assertNull(GroupSizeLayout.resolve(10, 3));
        List<Integer> opts = GroupSizeLayout.calcGroupSizeOptions(10);
        assertEquals(List.of(2, 5, 10), opts);
    }

    private static int ArraysSum(int[] a) {
        int s = 0;
        for (int v : a) s += v;
        return s;
    }
}
