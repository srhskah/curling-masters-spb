package com.example.controller;

import com.example.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Cookie 管理控制器
 * 提供Cookie的查看、设置、删除等功能
 */
@Controller
@RequestMapping("/cookie")
public class CookieController {

    /**
     * 查看所有Cookie
     */
    @GetMapping("/view")
    @ResponseBody
    public Map<String, Object> viewCookies(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        Map<String, String> cookieMap = new HashMap<>();
        
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie cookie : cookies) {
                try {
                    String value = java.net.URLDecoder.decode(cookie.getValue(), java.nio.charset.StandardCharsets.UTF_8);
                    cookieMap.put(cookie.getName(), value);
                } catch (Exception e) {
                    cookieMap.put(cookie.getName(), cookie.getValue());
                }
            }
        }
        
        result.put("cookies", cookieMap);
        result.put("count", cookieMap.size());
        return result;
    }

    /**
     * 设置Cookie
     */
    @PostMapping("/set")
    @ResponseBody
    public Map<String, Object> setCookie(@RequestParam String name,
                                        @RequestParam String value,
                                        @RequestParam(required = false, defaultValue = "3600") Integer maxAge,
                                        HttpServletResponse response) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            CookieUtil.addCookie(response, name, value, maxAge);
            result.put("success", true);
            result.put("message", "Cookie设置成功");
            result.put("name", name);
            result.put("value", value);
            result.put("maxAge", maxAge);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Cookie设置失败: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 删除Cookie
     */
    @DeleteMapping("/remove")
    @ResponseBody
    public Map<String, Object> removeCookie(@RequestParam String name,
                                           HttpServletResponse response) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            CookieUtil.removeCookie(response, name);
            result.put("success", true);
            result.put("message", "Cookie删除成功");
            result.put("name", name);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Cookie删除失败: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 清除所有应用相关的Cookie
     */
    @DeleteMapping("/clear")
    @ResponseBody
    public Map<String, Object> clearAllCookies(HttpServletResponse response) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 清除所有应用相关的Cookie
            String[] cookieNames = {
                "remembered_username", "user_theme", "user_language", 
                "remember_me", "login_username", "login_token"
            };
            
            for (String name : cookieNames) {
                CookieUtil.removeCookie(response, name);
            }
            
            result.put("success", true);
            result.put("message", "所有应用Cookie已清除");
            result.put("clearedCookies", cookieNames);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "清除Cookie失败: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 获取用户偏好设置
     */
    @GetMapping("/preferences")
    @ResponseBody
    public Map<String, Object> getUserPreferences(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        
        result.put("theme", CookieUtil.getCookie(request, "user_theme"));
        result.put("language", CookieUtil.getCookie(request, "user_language"));
        result.put("rememberMe", "true".equals(CookieUtil.getCookie(request, "remember_me")));
        result.put("rememberedUsername", CookieUtil.getCookie(request, "remembered_username"));
        result.put("loginToken", CookieUtil.getCookie(request, "login_token"));
        
        return result;
    }

    /**
     * 保存用户偏好设置
     */
    @PostMapping("/preferences")
    @ResponseBody
    public Map<String, Object> saveUserPreferences(@RequestParam(required = false) String theme,
                                                  @RequestParam(required = false) String language,
                                                  @RequestParam(required = false, defaultValue = "false") Boolean rememberMe,
                                                  HttpServletResponse response) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            CookieUtil.saveUserPreferences(response, theme, language, rememberMe);
            
            result.put("success", true);
            result.put("message", "用户偏好设置保存成功");
            result.put("preferences", Map.of(
                "theme", theme,
                "language", language,
                "rememberMe", rememberMe
            ));
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "保存偏好设置失败: " + e.getMessage());
        }
        
        return result;
    }
}
