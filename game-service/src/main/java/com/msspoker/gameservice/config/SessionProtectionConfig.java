package com.msspoker.gameservice.config;

import com.msspoker.common.security.SessionAccessClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
public class SessionProtectionConfig {
    // Live HTTP checks must not delay the game engine's scheduler during an auth outage.
    @Bean
    ThreadPoolTaskScheduler sessionAuthorityScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("game-session-check-");
        return scheduler;
    }

    @Bean
    SessionAccessClient sessionAccessClient(@Value("${auth.session.check-url}") String url,
            @Value("${auth.session.internal-key}") String key) {
        return new SessionAccessClient(url, key);
    }
}
