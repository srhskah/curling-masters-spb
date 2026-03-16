package com.example.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * 时区工具类
 * 用于验证和显示系统时区设置
 */
public class TimezoneUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(TimezoneUtil.class);
    
    /**
     * 验证和记录当前时区设置
     */
    public static void logTimezoneInfo() {
        logger.info("=== 时区配置信息 ===");
        
        // 系统默认时区
        TimeZone defaultTimeZone = TimeZone.getDefault();
        logger.info("系统默认时区: {}", defaultTimeZone.getID());
        logger.info("时区显示名称: {}", defaultTimeZone.getDisplayName());
        logger.info("时区偏移量: {} 小时", defaultTimeZone.getRawOffset() / (1000 * 60 * 60));
        
        // Java 8 时间API时区
        ZoneId systemZone = ZoneId.systemDefault();
        logger.info("ZoneId系统默认: {}", systemZone.getId());
        
        // 当前时间
        Date currentDate = new Date();
        LocalDateTime localDateTime = LocalDateTime.now();
        ZonedDateTime zonedDateTime = ZonedDateTime.now();
        
        logger.info("当前Date对象时间: {}", currentDate);
        logger.info("当前LocalDateTime: {}", localDateTime);
        logger.info("当前ZonedDateTime: {}", zonedDateTime);
        
        // 指定时区的当前时间
        ZonedDateTime shanghaiTime = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
        ZonedDateTime utcTime = ZonedDateTime.now(ZoneId.of("UTC"));
        
        logger.info("上海时区当前时间: {}", shanghaiTime);
        logger.info("UTC时区当前时间: {}", utcTime);
        
        // 日历时区
        Calendar calendar = Calendar.getInstance();
        logger.info("Calendar时区: {}", calendar.getTimeZone().getID());
        logger.info("Calendar当前时间: {}", calendar.getTime());
        
        // 系统属性
        String userTimezone = System.getProperty("user.timezone");
        String fileEncoding = System.getProperty("file.encoding");
        
        logger.info("系统属性user.timezone: {}", userTimezone);
        logger.info("系统属性file.encoding: {}", fileEncoding);
        
        logger.info("=== 时区配置验证完成 ===");
    }
    
    /**
     * 获取当前上海时区时间
     */
    public static ZonedDateTime getCurrentShanghaiTime() {
        return ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
    }
    
    /**
     * 检查是否使用正确的时区
     */
    public static boolean isCorrectTimezone() {
        String defaultTimezone = TimeZone.getDefault().getID();
        return "Asia/Shanghai".equals(defaultTimezone);
    }
}