package com.example.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TournamentUrlServiceCompactTest {

    @Test
    void compact_2026_half2_series5() {
        assertEquals("260205", TournamentUrlService.compactCode(2026, 2, 5));
        TournamentUrlService.CompactParts parts = TournamentUrlService.parseCompact("260205");
        assertEquals(2026, parts.year());
        assertEquals(2, parts.half());
        assertEquals(5, parts.seriesSequence());
    }

    @Test
    void compact_upperHalfPadded() {
        assertEquals("250101", TournamentUrlService.compactCode(2025, 1, 1));
    }

    @Test
    void compact_rejectsOutOfRange() {
        assertNull(TournamentUrlService.compactCode(1999, 1, 1));
        assertNull(TournamentUrlService.compactCode(2026, 3, 1));
        assertNull(TournamentUrlService.compactCode(2026, 1, 100));
        assertNull(TournamentUrlService.parseCompact("260005"));
        assertNull(TournamentUrlService.parseCompact("2602"));
        assertNull(TournamentUrlService.parseCompact(null));
    }
}
