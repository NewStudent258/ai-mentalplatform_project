package org.example.aispingboot.config;

import cn.hutool.core.text.AntPathMatcher;
import org.example.aispingboot.util.JwtAuthticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    private static final AntPathMatcher antPathMatcher = new AntPathMatcher();
    private  static final String[] PUBLIC_PATHS = {
            "/",
            // 错误转发端点必须放行。Spring Boot 会把未捕获异常与 404 转发到 /error，
            // 若 /error 需要认证，返回给客户端的会是 401/403 而不是真实的 404/500——
            // 这会把「接口不存在」伪装成「无权限」，前端据此清除登录态，导致用户被莫名登出。
            "/error",
            "/api/test",
            "/api/user/login",
            "/api/user/add",
            "/api/knowledge/article/page",
            "/uploads/**"
    };

    public static Boolean isPublicPATH(String method, String requestUri) {
        // 知识文章详情 GET 公开（写入/管理操作需认证）
        if (HttpMethod.GET.matches(method) && antPathMatcher.match("/api/knowledge/article/*", requestUri)) {
            return true;
        }
        for (String publicPath : PUBLIC_PATHS) {
            if (antPathMatcher.match(publicPath, requestUri)) {
                return true;
            }
        }
        return false;
    }

    @Bean
    public JwtAuthticationFilter jwtAuthticationFilter() {
        return new JwtAuthticationFilter();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 禁用CSRF保护 （API服务通常不需要）
                .csrf(AbstractHttpConfigurer::disable)
                // 配置会话管理为无状态（JWT需要）
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 配置请求的授权规则
                .authorizeHttpRequests(auth -> auth
                        // 公开的路径，无需登录即可访问
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // 知识文章详情 GET 公开
                        .requestMatchers(HttpMethod.GET, "/api/knowledge/article/*").permitAll()
                        // 其他请求都需要认证
                        .anyRequest().authenticated()
                )
                // 添加JWT认证过滤器
                .addFilterBefore(jwtAuthticationFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
