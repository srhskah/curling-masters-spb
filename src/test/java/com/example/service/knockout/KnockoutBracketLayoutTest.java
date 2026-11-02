package com.example.service.knockout;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnockoutBracketLayoutTest {

    private record Item(Integer half, Integer slot, String name) {}

    @Test
    void prefersKnockoutHalfOverSlot() {
        assertTrue(KnockoutBracketLayout.isUpper(0, 7, 8));
        assertFalse(KnockoutBracketLayout.isUpper(1, 0, 8));
    }

    @Test
    void slotFallbackSplitsByRoundSize() {
        assertTrue(KnockoutBracketLayout.isUpper(null, 0, 8));
        assertTrue(KnockoutBracketLayout.isUpper(null, 3, 8));
        assertFalse(KnockoutBracketLayout.isUpper(null, 4, 8));
        assertTrue(KnockoutBracketLayout.isUpper(null, 0, 4));
        assertFalse(KnockoutBracketLayout.isUpper(null, 2, 4));
    }

    @Test
    void worldCupInterleavedSlotsRegroupToHalves() {
        List<Item> src = List.of(
                new Item(0, 0, "U0"),
                new Item(1, 1, "L0"),
                new Item(0, 2, "U1"),
                new Item(1, 3, "L1"),
                new Item(0, 4, "U2"),
                new Item(1, 5, "L2"),
                new Item(0, 6, "U3"),
                new Item(1, 7, "L3")
        );
        List<Item> laid = KnockoutBracketLayout.placeVisualSlots(
                src, 8, Item::half, Item::slot);
        assertEquals("U0", laid.get(0).name);
        assertEquals("U1", laid.get(1).name);
        assertEquals("U2", laid.get(2).name);
        assertEquals("U3", laid.get(3).name);
        assertEquals("L0", laid.get(4).name);
        assertEquals("L1", laid.get(5).name);
        assertEquals("L2", laid.get(6).name);
        assertEquals("L3", laid.get(7).name);
    }

    @Test
    void laterRoundSlotRestartPerHalf() {
        List<Item> qf = List.of(
                new Item(0, 0, "UQ0"),
                new Item(0, 1, "UQ1"),
                new Item(1, 0, "LQ0"),
                new Item(1, 1, "LQ1")
        );
        List<Item> laid = KnockoutBracketLayout.placeVisualSlots(
                qf, 4, Item::half, Item::slot);
        assertEquals("UQ0", laid.get(0).name);
        assertEquals("UQ1", laid.get(1).name);
        assertEquals("LQ0", laid.get(2).name);
        assertEquals("LQ1", laid.get(3).name);
    }

    @Test
    void classicFirstRoundSplitInfersHalves() {
        assertEquals(0, KnockoutBracketLayout.inferHalfFromFirstRoundSplit(0, 8));
        assertEquals(0, KnockoutBracketLayout.inferHalfFromFirstRoundSplit(3, 8));
        assertEquals(1, KnockoutBracketLayout.inferHalfFromFirstRoundSplit(4, 8));
        assertEquals(1, KnockoutBracketLayout.inferHalfFromFirstRoundSplit(7, 8));
        assertEquals(0, KnockoutBracketLayout.inferHalfFromFirstRoundSplit(0, 4));
        assertEquals(1, KnockoutBracketLayout.inferHalfFromFirstRoundSplit(2, 4));
        assertNull(KnockoutBracketLayout.inferHalfFromFirstRoundSplit(0, 1));
    }

    @Test
    void classicInferredHalvesLayoutUpperThenLower() {
        List<Item> src = List.of(
                new Item(null, 0, "1v16"),
                new Item(null, 1, "8v9"),
                new Item(null, 2, "4v13"),
                new Item(null, 3, "5v12"),
                new Item(null, 4, "2v15"),
                new Item(null, 5, "7v10"),
                new Item(null, 6, "3v14"),
                new Item(null, 7, "6v11")
        );
        List<Item> inferred = src.stream()
                .map(it -> new Item(KnockoutBracketLayout.inferHalfFromFirstRoundSplit(it.slot, 8), it.slot, it.name))
                .toList();
        List<Item> laid = KnockoutBracketLayout.placeVisualSlots(
                inferred, 8, Item::half, Item::slot);
        assertEquals("1v16", laid.get(0).name);
        assertEquals("5v12", laid.get(3).name);
        assertEquals("2v15", laid.get(4).name);
        assertEquals("6v11", laid.get(7).name);
        assertEquals(0, laid.get(0).half);
        assertEquals(1, laid.get(4).half);
    }

    @Test
    void classicModeKeepsGlobalSlot() {
        List<Item> src = List.of(
                new Item(null, 0, "A"),
                new Item(null, 3, "D"),
                new Item(null, 4, "E")
        );
        List<Item> laid = KnockoutBracketLayout.placeVisualSlots(
                src, 8, Item::half, Item::slot);
        assertEquals("A", laid.get(0).name);
        assertNull(laid.get(1));
        assertEquals("D", laid.get(3).name);
        assertEquals("E", laid.get(4).name);
    }
}
