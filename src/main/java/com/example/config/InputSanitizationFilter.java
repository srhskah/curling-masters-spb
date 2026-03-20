package com.example.config;

import com.example.util.InputSanitizer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 全局请求参数净化器：
 * 把所有表单/查询参数里的“明显危险片段”和控制字符去掉，降低 XSS/注入类风险。
 *
 * 过滤器层面不做“严格拒绝”，避免误伤正常业务数据；严格校验仍应在具体业务层完成。
 */
@Component
public class InputSanitizationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        Map<String, String[]> paramMap = request.getParameterMap();
        if (paramMap == null || paramMap.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        Map<String, String[]> sanitized = new HashMap<>();
        for (Map.Entry<String, String[]> e : paramMap.entrySet()) {
            String key = e.getKey();
            String[] values = e.getValue();
            if (values == null) {
                sanitized.put(key, null);
                continue;
            }

            // 跳过密码相关参数，避免改变密码导致登录/校验失败
            if (key != null && key.toLowerCase(Locale.ROOT).contains("password")) {
                sanitized.put(key, values);
                continue;
            }

            // 跳过 CSRF token，避免 token 值被净化破坏导致校验失败
            if (key != null && key.toLowerCase(Locale.ROOT).contains("csrf")) {
                sanitized.put(key, values);
                continue;
            }

            String[] clean = Arrays.stream(values)
                    .map(InputSanitizer::sanitize)
                    .toArray(String[]::new);
            sanitized.put(key, clean);
        }

        HttpServletRequest wrapper = new HttpServletRequestWrapper(request) {
            @Override
            public String getParameter(String name) {
                String[] values = getParameterValues(name);
                return (values != null && values.length > 0) ? values[0] : null;
            }

            @Override
            public Map<String, String[]> getParameterMap() {
                return Collections.unmodifiableMap(sanitized);
            }

            @Override
            public String[] getParameterValues(String name) {
                return sanitized.get(name);
            }
        };

        filterChain.doFilter(wrapper, response);
    }
}

