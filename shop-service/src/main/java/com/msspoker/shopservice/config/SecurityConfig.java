package com.msspoker.shopservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cấu hình Spring Security nền tảng cho shop-service.
 *
 * Lưu ý nghiệp vụ:
 * 1. Đã bật @EnableMethodSecurity(prePostEnabled = true) để sẵn sàng sử dụng @PreAuthorize("...")
 * 2. Tạm thời cấu hình anyRequest().permitAll() để không chặn request hoặc làm gãy luồng test
 *    khi auth-service đang trong quá trình hoàn thiện.
 * 3. Khi auth-service hoàn tất phát hành JWT Bearer Token, cấu hình Resource Server / JWT filter
 *    sẽ được tích hợp trực tiếp tại đây mà không cần đập đi xây lại cấu trúc.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Tạm thời cho phép tất cả các request đi qua (chưa phân quyền cứng)
                        .anyRequest().permitAll()
                );

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }
}
