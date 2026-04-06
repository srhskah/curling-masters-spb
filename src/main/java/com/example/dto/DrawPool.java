package com.example.dto;

/**
 * 正赛+资格赛模式下抽签分池：与 {@code tournament_draw.draw_pool} 一致。
 */
public enum DrawPool {
    MAIN,
    QUALIFIER;

    public static DrawPool fromParam(String raw) {
        if (raw == null || raw.isBlank()) {
            return MAIN;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return MAIN;
        }
    }
}
