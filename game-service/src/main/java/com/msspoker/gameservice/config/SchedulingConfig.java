package com.msspoker.gameservice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "game.scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
    private static final int IO_CONCURRENCY = 4;

    @Bean(destroyMethod = "close")
    public SimpleAsyncTaskScheduler taskScheduler() {
        SimpleAsyncTaskScheduler scheduler = new SimpleAsyncTaskScheduler();
        scheduler.setVirtualThreads(true);
        scheduler.setConcurrencyLimit(IO_CONCURRENCY);
        scheduler.setThreadNamePrefix("game-schedule-");
        return scheduler;
    }
}
