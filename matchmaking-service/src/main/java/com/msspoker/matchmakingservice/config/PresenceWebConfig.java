package com.msspoker.matchmakingservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class PresenceWebConfig implements WebMvcConfigurer {
    private final PresenceInterceptor presence;

    public PresenceWebConfig(PresenceInterceptor presence) {
        this.presence = presence;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(presence).addPathPatterns("/api/matchmaking/**", "/api/party/**");
    }
}
