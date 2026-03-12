package com.example.controller;

import com.example.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private static final Logger logger = LoggerFactory.getLogger(HomeController.class);

    @GetMapping("/")
    public String home(Authentication authentication, Model model, HttpServletRequest request) {
        logger.info("HomeController.home() called - Testing new log configuration");
        logger.info("Current timestamp for testing: {}", new java.util.Date());
        if (authentication != null) {
            logger.info("Authentication name: {}", authentication.getName());
            logger.info("Authentication principal: {}", authentication.getPrincipal());
            logger.info("Authentication authorities: {}", authentication.getAuthorities());
        }

        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            logger.info("Setting username to model: {}", username);
            model.addAttribute("username", username);
            model.addAttribute("authorities", authentication.getAuthorities());
        } else {
            logger.info("No authentication or not authenticated");
        }
        
        // 从Cookie读取用户偏好设置
        String theme = CookieUtil.getCookie(request, "user_theme");
        String language = CookieUtil.getCookie(request, "user_language");
        String rememberMe = CookieUtil.getCookie(request, "remember_me");
        
        if (theme != null) {
            model.addAttribute("userTheme", theme);
        }
        if (language != null) {
            model.addAttribute("userLanguage", language);
        }
        if (rememberMe != null) {
            model.addAttribute("rememberMe", "true".equals(rememberMe));
        }
        
        // 读取登录信息
        String loginToken = CookieUtil.getCookie(request, "login_token");
        if (loginToken != null) {
            model.addAttribute("loginToken", loginToken);
        }
        
        return "home";
    }
}
