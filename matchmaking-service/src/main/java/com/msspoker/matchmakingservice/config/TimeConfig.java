package com.msspoker.matchmakingservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TimeConfig {
    @Bean
    Clock utcClock() {
        return Clock.systemUTC();
    }
}
