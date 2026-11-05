package com.example.util;

/**
 * 单次导出地址上的 {@code orientation=portrait|landscape}，只覆盖这一次，不改全局默认。
 */
public final class PdfOrientationHolder {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private PdfOrientationHolder() {
    }

    public static void set(String orientation) {
        CURRENT.set(orientation);
    }

    public static String get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
