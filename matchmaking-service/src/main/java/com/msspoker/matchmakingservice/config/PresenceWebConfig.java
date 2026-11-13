package com.msspoker.matchmakingservice.config;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class PresenceWebConfig implements WebMvcConfigurer {
    private final PresenceInterceptor presence;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(presence).addPathPatterns("/api/matchmaking/**", "/api/party/**");
    }
}
