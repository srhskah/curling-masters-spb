package com.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Spring Session JDBC 配置
 * 将会话存储到数据库中，实现服务器重启后会话不丢失
 */
@Configuration
@EnableJdbcHttpSession(maxInactiveIntervalInSeconds = 604800) // 默认7天
public class HttpSessionConfig {

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("SESSIONID");
        serializer.setCookiePath("/");
        serializer.setDomainNamePattern("^.+?\\.(\\w+\\.[a-z]+)$");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(false); // 生产环境建议改为true并使用HTTPS
        serializer.setSameSite("Lax");
        return serializer;
    }
}
