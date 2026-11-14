package com.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;

/**
 * Web configuration for CORS and static resources
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 0=开发不缓存；生产 application-prod 设 86400，样式文件不变时减少重复读盘/压缩 CPU */
    @Value("${app.static-cache-seconds:0}")
    private int staticCacheSeconds;

    @PostConstruct
    public void init() {
        // 确保Web层使用正确的时区
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        int cache = Math.max(0, staticCacheSeconds);
        // 外置 /app/static 优先（docker-compose 挂载工程根目录 static/），否则改 CSS/JS 刷新无效
        registry.addResourceHandler("/static/**")
                .addResourceLocations("file:/app/static/")
                .setCachePeriod(cache);

        registry.addResourceHandler("/css/**")
                .addResourceLocations("file:/app/static/css/")
                .setCachePeriod(cache);

        registry.addResourceHandler("/js/**")
                .addResourceLocations("file:/app/static/js/")
                .setCachePeriod(cache);

        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:/app/static/images/")
                .setCachePeriod(cache);
    }

}