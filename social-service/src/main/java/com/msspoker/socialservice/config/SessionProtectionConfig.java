package com.msspoker.socialservice.config;

import com.msspoker.common.security.SessionAccessClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SessionProtectionConfig {
    @Bean
    SessionAccessClient sessionAccessClient(
            @Value("${auth.session.check-url}") String url,
            @Value("${auth.session.internal-key}") String key) {
        return new SessionAccessClient(url, key);
    }
}
