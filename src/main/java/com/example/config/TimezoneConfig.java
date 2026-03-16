package com.example.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * 时区配置类
 * 确保整个应用使用 Asia/Shanghai 时区
 */
@Configuration
public class TimezoneConfig {

    @PostConstruct
    public void init() {
        // 设置默认时区为 Asia/Shanghai
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        System.setProperty("user.timezone", "Asia/Shanghai");
        
        // 验证时区设置
        TimeZone defaultTimeZone = TimeZone.getDefault();
        System.out.println("应用时区已设置为: " + defaultTimeZone.getID());
        System.out.println("时区显示名称: " + defaultTimeZone.getDisplayName());
        System.out.println("当前时间: " + new java.util.Date());
    }
}