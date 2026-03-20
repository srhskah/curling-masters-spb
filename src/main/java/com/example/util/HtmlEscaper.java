package com.example.util;

/**
 * 简单 HTML 转义工具：用于把“动态文本”安全嵌入到 HTML 字符串里，
 * 防止把用户输入直接变成可执行的标签/属性。
 */
public final class HtmlEscaper {

    private HtmlEscaper() {}

    public static String escapeHtml(String input) {
        if (input == null) return "";

        // 先替换 &，避免二次转义
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}

