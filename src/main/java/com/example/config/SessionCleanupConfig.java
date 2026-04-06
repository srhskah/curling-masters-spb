package com.example.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 定时清理过期会话
 * Spring Session JDBC 会自动清理过期会话，但为了确保数据库不会积累过多数据，
 * 我们添加一个定时任务来定期清理
 */
@Configuration
@EnableScheduling
public class SessionCleanupConfig {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 每天凌晨2点清理过期会话
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupExpiredSessions() {
        try {
            long currentTime = System.currentTimeMillis();
            int deletedCount = jdbcTemplate.update(
                "DELETE FROM SPRING_SESSION WHERE EXPIRY_TIME < ?",
                currentTime
            );
            System.out.println("Session cleanup: Deleted " + deletedCount + " expired sessions");
        } catch (Exception e) {
            System.err.println("Error cleaning up expired sessions: " + e.getMessage());
        }
    }
}
