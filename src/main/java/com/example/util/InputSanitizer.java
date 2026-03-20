package com.example.util;

import java.util.regex.Pattern;

/**
 * 对请求入参做轻量净化，主要用于：
 * 1) 移除控制字符，避免日志/页面/JS 拼接时出现异常
 * 2) 屏蔽常见的明显 XSS 片段（例如 javascript: / script / onerror 等）
 *
 * 注意：密码字段会在过滤器中跳过净化，避免导致登录失败。
 */
public final class InputSanitizer {

    private InputSanitizer() {}

    // 控制字符（包含除空白外的不可见字符）
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

    // 常见危险片段（尽量不做“全量去除”，避免破坏正常文本）
    private static final Pattern[] DANGEROUS_PATTERNS = new Pattern[] {
        Pattern.compile("(?i)javascript\\s*:", Pattern.MULTILINE),
        Pattern.compile("(?i)<\\s*script", Pattern.MULTILINE),
        Pattern.compile("(?i)</\\s*script\\s*>", Pattern.MULTILINE),
        Pattern.compile("(?i)onerror\\s*=", Pattern.MULTILINE),
        Pattern.compile("(?i)onload\\s*=", Pattern.MULTILINE)
    };

    public static String sanitize(String input) {
        if (input == null) return null;

        // 移除 null byte，避免后续处理链路异常
        String s = input.replace("\0", "");

        // CR/LF/Tab 统一替换为空格
        s = s.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');

        // 去掉其它控制字符
        s = CONTROL_CHARS.matcher(s).replaceAll("");

        // 屏蔽常见危险片段（不转义，避免“被渲染成 HTML 后仍可执行”）
        for (Pattern p : DANGEROUS_PATTERNS) {
            s = p.matcher(s).replaceAll("");
        }

        return s.trim();
    }

    /**
     * 保留换行/回车（用于“名单/排名粘贴”这类依赖换行分割的输入）。
     * 仍会做控制字符清理与明显危险片段移除，但不会把 \n 替换为空格。
     */
    public static String sanitizeAllowNewlines(String input) {
        if (input == null) return null;

        String s = input.replace("\0", "");

        // 不替换 \r/\n/\t，允许业务通过换行分隔。
        // 但仍移除其它控制字符（不包含 \r/\n/\t）。
        s = CONTROL_CHARS.matcher(s).replaceAll("");

        // 屏蔽常见危险片段
        for (Pattern p : DANGEROUS_PATTERNS) {
            s = p.matcher(s).replaceAll("");
        }

        return s.trim();
    }
}

