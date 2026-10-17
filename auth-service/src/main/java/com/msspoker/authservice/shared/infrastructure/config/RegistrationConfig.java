package com.msspoker.authservice.shared.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({OtpProperties.class, AuthMailProperties.class})
public class RegistrationConfig {
    @Bean
    Clock authClock() {
        return Clock.systemUTC();
    }
}
