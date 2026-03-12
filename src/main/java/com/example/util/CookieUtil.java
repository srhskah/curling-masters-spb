package com.example.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Cookie 工具类
 * 提供Cookie的创建、读取、删除等功能
 */
public class CookieUtil {

    // 默认Cookie过期时间（7天）
    private static final int DEFAULT_MAX_AGE = 7 * 24 * 60 * 60;
    
    // Cookie路径
    private static final String COOKIE_PATH = "/";
    
    // Cookie域名（根据需要设置）
    private static final String COOKIE_DOMAIN = null;

    /**
     * 添加Cookie
     * @param response HTTP响应对象
     * @param name Cookie名称
     * @param value Cookie值
     */
    public static void addCookie(HttpServletResponse response, String name, String value) {
        addCookie(response, name, value, DEFAULT_MAX_AGE, COOKIE_PATH, COOKIE_DOMAIN, false);
    }

    /**
     * 添加Cookie（指定过期时间）
     * @param response HTTP响应对象
     * @param name Cookie名称
     * @param value Cookie值
     * @param maxAge 过期时间（秒）
     */
    public static void addCookie(HttpServletResponse response, String name, String value, int maxAge) {
        addCookie(response, name, value, maxAge, COOKIE_PATH, COOKIE_DOMAIN, false);
    }

    /**
     * 添加Cookie（完整参数）
     * @param response HTTP响应对象
     * @param name Cookie名称
     * @param value Cookie值
     * @param maxAge 过期时间（秒）
     * @param path Cookie路径
     * @param domain Cookie域名
     * @param httpOnly 是否仅HTTP访问
     */
    public static void addCookie(HttpServletResponse response, String name, String value, 
                                int maxAge, String path, String domain, boolean httpOnly) {
        try {
            // 对值进行URL编码，防止中文乱码
            String encodedValue = URLEncoder.encode(value, StandardCharsets.UTF_8);
            
            Cookie cookie = new Cookie(name, encodedValue);
            cookie.setMaxAge(maxAge);
            cookie.setPath(StringUtils.hasText(path) ? path : COOKIE_PATH);
            
            if (StringUtils.hasText(domain)) {
                cookie.setDomain(domain);
            }
            
            cookie.setHttpOnly(httpOnly);
            // 设置Secure属性（仅在HTTPS下传输，生产环境建议开启）
            // cookie.setSecure(true);
            
            response.addCookie(cookie);
        } catch (Exception e) {
            throw new RuntimeException("添加Cookie失败", e);
        }
    }

    /**
     * 获取Cookie值
     * @param request HTTP请求对象
     * @param name Cookie名称
     * @return Cookie值，如果不存在返回null
     */
    public static String getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                try {
                    // 对值进行URL解码
                    return URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8);
                } catch (Exception e) {
                    return cookie.getValue(); // 解码失败返回原值
                }
            }
        }
        return null;
    }

    /**
     * 获取Cookie值（带默认值）
     * @param request HTTP请求对象
     * @param name Cookie名称
     * @param defaultValue 默认值
     * @return Cookie值，如果不存在返回默认值
     */
    public static String getCookie(HttpServletRequest request, String name, String defaultValue) {
        String value = getCookie(request, name);
        return value != null ? value : defaultValue;
    }

    /**
     * 删除Cookie
     * @param response HTTP响应对象
     * @param name Cookie名称
     */
    public static void removeCookie(HttpServletResponse response, String name) {
        removeCookie(response, name, COOKIE_PATH, COOKIE_DOMAIN);
    }

    /**
     * 删除Cookie（指定路径和域名）
     * @param response HTTP响应对象
     * @param name Cookie名称
     * @param path Cookie路径
     * @param domain Cookie域名
     */
    public static void removeCookie(HttpServletResponse response, String name, String path, String domain) {
        Cookie cookie = new Cookie(name, "");
        cookie.setMaxAge(0);
        cookie.setPath(StringUtils.hasText(path) ? path : COOKIE_PATH);
        
        if (StringUtils.hasText(domain)) {
            cookie.setDomain(domain);
        }
        
        response.addCookie(cookie);
    }

    /**
     * 检查Cookie是否存在
     * @param request HTTP请求对象
     * @param name Cookie名称
     * @return 是否存在
     */
    public static boolean hasCookie(HttpServletRequest request, String name) {
        return getCookie(request, name) != null;
    }

    /**
     * 保存用户偏好设置Cookie
     * @param response HTTP响应对象
     * @param theme 主题
     * @param language 语言
     * @param rememberMe 记住我
     */
    public static void saveUserPreferences(HttpServletResponse response, 
                                         String theme, String language, Boolean rememberMe) {
        if (StringUtils.hasText(theme)) {
            addCookie(response, "user_theme", theme, 30 * 24 * 60 * 60); // 30天
        }
        if (StringUtils.hasText(language)) {
            addCookie(response, "user_language", language, 30 * 24 * 60 * 60); // 30天
        }
        if (rememberMe != null) {
            addCookie(response, "remember_me", rememberMe.toString(), 7 * 24 * 60 * 60); // 7天
        }
    }

    /**
     * 保存登录状态Cookie
     * @param response HTTP响应对象
     * @param username 用户名
     * @param token 登录令牌
     */
    public static void saveLoginCookie(HttpServletResponse response, String username, String token) {
        addCookie(response, "login_username", username, 7 * 24 * 60 * 60); // 7天
        addCookie(response, "login_token", token, 7 * 24 * 60 * 60); // 7天
    }

    /**
     * 清除所有登录相关Cookie
     * @param response HTTP响应对象
     */
    public static void clearLoginCookies(HttpServletResponse response) {
        removeCookie(response, "login_username");
        removeCookie(response, "login_token");
        removeCookie(response, "remember_me");
    }
}
