package com.example.util;

import org.springframework.stereotype.Component;

/**
 * Thymeleaf 模板工具类
 */
@Component("thymeleafUtils")
public class ThymeleafUtils {
    
    /**
     * 生成用户主页链接
     * @param userId 用户ID
     * @return 用户主页URL，如果userId为null则返回#
     */
    public String getUserProfileLink(Long userId) {
        if (userId == null) {
            return "#";
        }
        return "/user/profile/" + userId;
    }
    
    /**
     * 检查用户名是否有效（非空且不是特殊值）
     * @param username 用户名
     * @return 是否有效
     */
    public boolean isValidUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        String trimmed = username.trim();
        return !trimmed.equals("待定") && 
               !trimmed.equals("未知") && 
               !trimmed.equals("退赛") &&
               !trimmed.equals("-");
    }
}
