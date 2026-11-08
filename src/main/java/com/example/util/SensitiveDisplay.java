package com.example.util;

import com.example.entity.User;

/**
 * 只处理展示用副本：清空密码、按需掩码邮箱。不写数据库。
 */
public final class SensitiveDisplay {

    private SensitiveDisplay() {
    }

    public static void clearPassword(User user) {
        if (user != null) {
            user.setPassword(null);
        }
    }

    public static void clearPasswords(Iterable<User> users) {
        if (users == null) {
            return;
        }
        for (User user : users) {
            clearPassword(user);
        }
    }

    /**
     * 本人或管理员保持原邮箱，便于编辑；其他人只看到掩码。调用方不得再把该对象 update 回库。
     */
    public static void maskEmailUnlessPrivileged(User user, boolean canSeeFullEmail) {
        if (user == null || canSeeFullEmail) {
            return;
        }
        user.setEmail(maskEmail(user.getEmail()));
    }

    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        int keep = local.length() <= 2 ? 1 : Math.min(5, local.length());
        return local.substring(0, keep) + "***" + domain;
    }
}
