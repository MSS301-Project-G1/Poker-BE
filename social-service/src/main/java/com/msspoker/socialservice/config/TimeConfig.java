package com.msspoker.socialservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TimeConfig {
    @Bean
    public org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler taskScheduler() {
        var s = new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        s.setPoolSize(2);
        return s;
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
