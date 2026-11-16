package com.example.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KnockoutBracketAdvanceTest {

    @Test
    void expectedNextMatchCount_quarterToSemi() {
        assertEquals(2, KnockoutBracketService.expectedNextMatchCount(4, 4));
        assertEquals(1, KnockoutBracketService.expectedNextMatchCount(4, 2));
    }

    @Test
    void expectedNextMatchCount_semiToMedals() {
        assertEquals(2, KnockoutBracketService.expectedNextMatchCount(2, 2));
    }

    @Test
    void expectedNextMatchCount_roundOf16() {
        assertEquals(4, KnockoutBracketService.expectedNextMatchCount(8, 8));
        assertEquals(8, KnockoutBracketService.expectedNextMatchCount(16, 16));
    }
}
