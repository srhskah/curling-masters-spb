package com.example.util;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * 用户名验证工具类
 * 支持Unicode字符和emoji，同时防止SQL注入
 */
public class UsernameValidator {

    // 支持Unicode字符和emoji的正则表达式
    // 允许：中文、日文、韩文、emoji、英文字母、数字、空格、下划线、连字符、点号，以及常用中英文标点（! ? ！ ？ 。 ( )）
    private static final Pattern VALID_USERNAME_PATTERN = Pattern.compile(
        "^[\\p{L}\\p{M}\\p{N}\\p{Zs}\\p{So}\\p{Sk}\\p{Sm}_.\\-!\\?！？。()]{1,50}$"
    );

    // SQL注入检测模式
    private static final Pattern[] SQL_INJECTION_PATTERNS = {
        Pattern.compile("(?i)(union|select|insert|update|delete|drop|create|alter|exec|execute)"),
        Pattern.compile("(?i)(script|javascript|vbscript|onload|onerror)"),
        Pattern.compile("(?i)(<|>|\"|'|--|;|/\\*|\\*/|xp_|sp_)"),
        Pattern.compile("(?i)(or|and)\\s+\\d+\\s*=\\s*\\d+"),
        Pattern.compile("(?i)(or|and)\\s+'[^']*'\\s*=\\s*'[^']*'")
    };

    // 危险字符检测
    private static final Pattern DANGEROUS_CHARS_PATTERN = Pattern.compile(
        "[<>\"'&;\\/*\\-\\-]"
    );

    // 控制字符检测（除了常见的空白字符）
    private static final Pattern CONTROL_CHARS_PATTERN = Pattern.compile(
        "[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]"
    );

    /**
     * 验证用户名是否有效
     * @param username 用户名
     * @return 验证结果
     */
    public static ValidationResult validateUsername(String username) {
        if (username == null) {
            return ValidationResult.error("用户名不能为空");
        }

        // 去除首尾空白字符
        String trimmedUsername = username.trim();
        
        if (trimmedUsername.isEmpty()) {
            return ValidationResult.error("用户名不能为空");
        }

        if (trimmedUsername.length() < 1) {
            return ValidationResult.error("用户名长度至少为1个字符");
        }

        if (trimmedUsername.length() > 50) {
            return ValidationResult.error("用户名长度不能超过50个字符");
        }

        // 检查控制字符
        if (CONTROL_CHARS_PATTERN.matcher(trimmedUsername).find()) {
            return ValidationResult.error("用户名包含非法字符");
        }

        // 检查SQL注入
        for (Pattern pattern : SQL_INJECTION_PATTERNS) {
            if (pattern.matcher(trimmedUsername).find()) {
                return ValidationResult.error("用户名包含不安全的内容");
            }
        }

        // 检查危险字符
        if (DANGEROUS_CHARS_PATTERN.matcher(trimmedUsername).find()) {
            return ValidationResult.error("用户名包含特殊字符");
        }

        // 检查是否符合允许的字符模式
        if (!VALID_USERNAME_PATTERN.matcher(trimmedUsername).matches()) {
            return ValidationResult.error("用户名只能包含字母、数字、中文、emoji、空格以及 _ - . ! ? ！ ？ 。 ( )");
        }

        // 检查是否全为空白字符
        if (trimmedUsername.chars().allMatch(Character::isWhitespace)) {
            return ValidationResult.error("用户名不能全为空白字符");
        }

        return ValidationResult.success();
    }

    /**
     * 清理用户名，移除潜在的危险字符
     * @param username 原始用户名
     * @return 清理后的用户名
     */
    public static String sanitizeUsername(String username) {
        if (username == null) {
            return null;
        }

        // 移除控制字符
        String cleaned = CONTROL_CHARS_PATTERN.matcher(username).replaceAll("");
        
        // 移除危险字符
        cleaned = DANGEROUS_CHARS_PATTERN.matcher(cleaned).replaceAll("");
        
        // 标准化Unicode字符
        cleaned = java.text.Normalizer.normalize(cleaned, java.text.Normalizer.Form.NFC);
        
        return cleaned.trim();
    }

    /**
     * 检查用户名是否包含管理员关键词
     * @param username 用户名
     * @return 是否包含管理员关键词
     */
    public static boolean containsAdminKeyword(String username) {
        if (username == null) {
            return false;
        }
        
        String lowerUsername = username.toLowerCase();
        return lowerUsername.contains("admin") || 
               lowerUsername.contains("管理员") || 
               lowerUsername.contains("administrator") ||
               lowerUsername.contains("root") ||
               lowerUsername.contains("superuser");
    }

    /**
     * 获取用户名的显示形式（安全处理）
     * @param username 用户名
     * @return 安全的显示形式
     */
    public static String getDisplayUsername(String username) {
        if (username == null) {
            return "未知用户";
        }
        
        // 转义HTML特殊字符
        return escapeHtml(username);
    }

    /**
     * 转义HTML特殊字符
     * @param input 输入字符串
     * @return 转义后的字符串
     */
    private static String escapeHtml(String input) {
        if (input == null) {
            return null;
        }
        
        return input.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#x27;");
    }

    /**
     * 检查用户名是否为纯emoji
     * @param username 用户名
     * @return 是否为纯emoji
     */
    public static boolean isPureEmoji(String username) {
        if (username == null) {
            return false;
        }
        
        // emoji范围（简化版本）
        Pattern emojiPattern = Pattern.compile(
            "^[\\p{So}\\p{Sk}]+$"
        );
        
        return emojiPattern.matcher(username).matches();
    }

    /**
     * 获取用户名的字符类型统计
     * @param username 用户名
     * @return 字符类型统计
     */
    public static CharacterStats getCharacterStats(String username) {
        if (username == null) {
            return new CharacterStats();
        }
        
        CharacterStats stats = new CharacterStats();
        
        for (int i = 0; i < username.length(); i++) {
            int codePoint = username.codePointAt(i);
            
            if (Character.isLetter(codePoint)) {
                stats.letters++;
            } else if (Character.isDigit(codePoint)) {
                stats.digits++;
            } else if (Character.isWhitespace(codePoint)) {
                stats.whitespace++;
            } else if (isEmoji(codePoint)) {
                stats.emojis++;
            } else if (isChinese(codePoint)) {
                stats.chinese++;
            } else {
                stats.others++;
            }
        }
        
        return stats;
    }

    private static boolean isEmoji(int codePoint) {
        return (codePoint >= 0x1F600 && codePoint <= 0x1F64F) || // 表情符号
               (codePoint >= 0x1F300 && codePoint <= 0x1F5FF) || // 杂项符号
               (codePoint >= 0x1F680 && codePoint <= 0x1F6FF) || // 交通和地图符号
               (codePoint >= 0x2600 && codePoint <= 0x26FF) ||   // 杂项符号
               (codePoint >= 0x2700 && codePoint <= 0x27BF);    // 装饰符号
    }

    private static boolean isChinese(int codePoint) {
        return (codePoint >= 0x4E00 && codePoint <= 0x9FFF) ||   // CJK统一汉字
               (codePoint >= 0x3400 && codePoint <= 0x4DBF) ||   // CJK扩展A
               (codePoint >= 0x20000 && codePoint <= 0x2A6DF) || // CJK扩展B
               (codePoint >= 0x2A700 && codePoint <= 0x2B73F) || // CJK扩展C
               (codePoint >= 0x2B740 && codePoint <= 0x2B81F) || // CJK扩展D
               (codePoint >= 0x2B820 && codePoint <= 0x2CEAF) || // CJK扩展E
               (codePoint >= 0x2CEB0 && codePoint <= 0x2EBEF) || // CJK扩展F
               (codePoint >= 0x3000 && codePoint <= 0x303F) ||   // CJK符号和标点
               (codePoint >= 0xFF00 && codePoint <= 0xFFEF);     // 全角ASCII、全角标点
    }

    /**
     * 验证结果类
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String message;

        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult error(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * 字符统计类
     */
    public static class CharacterStats {
        public int letters = 0;
        public int digits = 0;
        public int whitespace = 0;
        public int emojis = 0;
        public int chinese = 0;
        public int others = 0;

        @Override
        public String toString() {
            return String.format("字母: %d, 数字: %d, 空白: %d, Emoji: %d, 中文: %d, 其他: %d", 
                               letters, digits, whitespace, emojis, chinese, others);
        }
    }
}
