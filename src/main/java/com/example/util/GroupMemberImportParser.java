package com.example.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 小组名单导入：支持逗号/分号/换行分隔；同一行内多个用户名可用空格分隔；
 * 用户名本身可含空格、下划线、emoji、括号等——通过与全库用户名的「最长优先」前缀匹配解析。
 */
public final class GroupMemberImportParser {

    private static final Pattern STRONG_DELIM = Pattern.compile("[,，;；]+|[\\r\\n]+");
    /** 各类 Unicode 空白归一为普通空格 */
    private static final Pattern UNICODE_SPACES = Pattern.compile(
            "[\\s\\u00A0\\u1680\\u180E\\u2000-\\u200A\\u202F\\u205F\\u3000]+");

    private GroupMemberImportParser() {}

    /**
     * @param raw           原始粘贴文本
     * @param knownUsernames 全库用户名，已按长度降序排列（调用方保证）
     * @return 解析出的用户名片段（与库中完全一致的成功匹配；无法匹配的为孤立 token）
     */
    public static List<String> parseUserTokens(String raw, List<String> knownUsernames) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        String normalized = normalizeImportText(raw);
        if (normalized.isEmpty()) {
            return out;
        }
        String[] segments = STRONG_DELIM.split(normalized);
        for (String seg : segments) {
            String s = UNICODE_SPACES.matcher(seg.trim()).replaceAll(" ").trim();
            if (s.isEmpty()) {
                continue;
            }
            out.addAll(greedyMatchSegment(s, knownUsernames));
        }
        return out;
    }

    static String normalizeImportText(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.replace('\uFEFF', ' ');
        // 常见 HTML 实体（粘贴自网页时）
        t = t.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ");
        return t;
    }

    private static List<String> greedyMatchSegment(String segment, List<String> sortedByLengthDesc) {
        List<String> out = new ArrayList<>();
        int i = 0;
        final String s = segment;
        final int n = s.length();
        while (i < n) {
            while (i < n && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
            if (i >= n) {
                break;
            }
            boolean matched = false;
            for (String name : sortedByLengthDesc) {
                if (name == null || name.isEmpty()) {
                    continue;
                }
                if (s.startsWith(name, i)) {
                    int end = i + name.length();
                    if (end == n || Character.isWhitespace(s.charAt(end))) {
                        out.add(name);
                        i = end;
                        matched = true;
                        break;
                    }
                }
            }
            if (!matched) {
                int j = i;
                while (j < n && !Character.isWhitespace(s.charAt(j))) {
                    j++;
                }
                out.add(s.substring(i, j));
                i = j;
            }
        }
        return out;
    }
}
