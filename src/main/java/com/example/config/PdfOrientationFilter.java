package com.example.config;

import com.example.util.PdfOrientationHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class PdfOrientationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String raw = request.getParameter("orientation");
        if (raw != null) {
            String o = raw.trim().toLowerCase(Locale.ROOT);
            if ("portrait".equals(o) || "landscape".equals(o)) {
                PdfOrientationHolder.set(o);
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            PdfOrientationHolder.clear();
        }
    }
}
