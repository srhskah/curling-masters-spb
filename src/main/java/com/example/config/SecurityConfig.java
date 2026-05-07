package com.example.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.entity.CustomUserDetails;
import com.example.entity.User;
import com.example.service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final UserService userService;

    public SecurityConfig(UserService userService) {
        this.userService = userService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            System.out.println("DEBUG: Attempting to login user: " + username);
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("username", username);
            User user = userService.getOne(queryWrapper);

            if (user == null) {
                System.out.println("DEBUG: User not found: " + username);
                throw new UsernameNotFoundException("用户不存在");
            }

            System.out.println("DEBUG: User found: " + user.getUsername() + ", role: " + user.getRole());

            String role = switch (user.getRole()) {
                case 0 -> "ROLE_SUPER_ADMIN";
                case 1 -> "ROLE_ADMIN";
                default -> "ROLE_USER";
            };

            System.out.println("DEBUG: Creating CustomUserDetails with role: " + role);

            return new CustomUserDetails(user);
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                // docker 健康检查等需要匿名访问（用 Ant matcher，避免 MVC matcher 匹配不到）
                .requestMatchers(new AntPathRequestMatcher("/actuator/**")).permitAll()
                .requestMatchers(
                        "/",
                        "/ranking", "/ranking/**",
                        "/h2h", "/h2h/**",
                        "/season/list", "/season/detail/**",
                        "/tournament/list", "/tournament/detail/**",
                        "/user/login", "/user/register", "/user/change-password",
                        "/css/**", "/js/**", "/images/**"
                ).permitAll()
                .requestMatchers(HttpMethod.GET, "/tournament/competition/match/detail/**").permitAll()
                .requestMatchers("/user/manage/**").hasAnyRole("SUPER_ADMIN", "ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/user/login")
                .loginProcessingUrl("/login")
                .successHandler((request, response, authentication) -> {
                    // 检查用户是否需要修改密码
                    CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
                    User user = userDetails.getUser();
                    
                    System.out.println("DEBUG: User " + user.getUsername() + " passwordChanged: " + user.getPasswordChanged());
                    
                    // 如果密码未被修改过（passwordChanged为false），则跳转到修改密码页面
                    if (Boolean.TRUE.equals(user.getPasswordChanged())) {
                        System.out.println("DEBUG: Redirecting to change password page for user: " + user.getUsername());
                        response.sendRedirect("/user/change-password");
                    } else {
                        System.out.println("DEBUG: Redirecting to home page for user: " + user.getUsername());
                        response.sendRedirect("/"); // 登录成功后跳转到首页
                    }
                })
                .failureHandler((request, response, exception) -> {
                    System.out.println("DEBUG: Login failed for user. Exception: " + exception.getMessage());
                    response.sendRedirect("/user/login?error=true");
                })
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/user/login?logout=true")
                .permitAll()
            )
            .csrf(csrf -> csrf
                // 用 Cookie 承载 CSRF token，前端 fetch 可以从 cookie 里读取并带到请求头
                // （同时表单会使用 thymeleaf 的 _csrf hidden input）
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            )
            // 允许同源页面在 iframe 中打开（赛事详情页弹窗内嵌 match 详情/录分）
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
            );

        return http.build();
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.requestRejectedHandler((request, response, requestRejectedException) ->
                handleRejectedRequest(response, requestRejectedException));
    }

    private static void handleRejectedRequest(HttpServletResponse response, RequestRejectedException ex) {
        try {
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Request rejected");
            }
        } catch (Exception ignored) {
            // 这里不再向外抛，避免污染日志
        }
    }
}
