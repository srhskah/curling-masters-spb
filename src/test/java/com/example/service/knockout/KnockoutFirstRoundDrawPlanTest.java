package com.example.service.knockout;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KnockoutFirstRoundDrawPlanTest {

    @Test
    void quarterfinalPotsMatchTheRequestedBands() {
        List<KnockoutFirstRoundDrawPlan.Pot> pots = KnockoutFirstRoundDrawPlan.mainPots(8);
        assertEquals(2, pots.size());
        assertArrayEquals(new int[]{1, 2}, pots.get(0).drawers());
        assertArrayEquals(new int[]{8, 7}, pots.get(0).prizes());
        assertArrayEquals(new int[]{3, 4}, pots.get(1).drawers());
        assertArrayEquals(new int[]{6, 5}, pots.get(1).prizes());
        assertNull(KnockoutFirstRoundDrawPlan.qualifierPot(8, 0));
    }

    @Test
    void roundOf16MountedQualifierPotsKeepQualifierIdentity() {
        List<KnockoutFirstRoundDrawPlan.Pot> pots = KnockoutFirstRoundDrawPlan.mainPots(16);
        assertArrayEquals(new int[]{1, 2}, pots.get(0).drawers());
        assertArrayEquals(new int[]{16, 15}, pots.get(0).prizes());
        assertArrayEquals(new int[]{3, 4}, pots.get(1).drawers());
        assertArrayEquals(new int[]{14, 13}, pots.get(1).prizes());
        assertArrayEquals(new int[]{5, 6}, pots.get(2).drawers());
        assertArrayEquals(new int[]{12, 11}, pots.get(2).prizes());
        assertArrayEquals(new int[]{7, 8}, pots.get(3).drawers());
        assertArrayEquals(new int[]{10, 9}, pots.get(3).prizes());
        assertEquals("第16名参加的资格赛胜者", KnockoutFirstRoundDrawPlan.prizeLabel(16, 16, 4));
        assertEquals("第12名", KnockoutFirstRoundDrawPlan.prizeLabel(12, 16, 4));

        KnockoutFirstRoundDrawPlan.Pot qualifier = KnockoutFirstRoundDrawPlan.qualifierPot(16, 4);
        assertArrayEquals(new int[]{13, 14, 15, 16}, qualifier.drawers());
        assertArrayEquals(new int[]{17, 18, 19, 20}, qualifier.prizes());
    }

    @Test
    void drawnNumbersFillClassicSlotsAndUpperHalfStaysFirst() {
        Map<Integer, Integer> drawn = new HashMap<>();
        drawn.put(1, 15);
        drawn.put(2, 16);
        drawn.put(3, 13);
        drawn.put(4, 14);
        drawn.put(5, 11);
        drawn.put(6, 12);
        drawn.put(7, 9);
        drawn.put(8, 10);
        List<int[]> matches = KnockoutFirstRoundDrawPlan.mainMatches(16, drawn);
        assertEquals(8, matches.size());
        assertArrayEquals(new int[]{1, 15}, matches.get(0));
        assertArrayEquals(new int[]{8, 10}, matches.get(1));
        assertArrayEquals(new int[]{2, 16}, matches.get(4));
    }
}
