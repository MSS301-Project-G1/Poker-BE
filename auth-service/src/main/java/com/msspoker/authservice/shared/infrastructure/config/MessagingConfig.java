package com.msspoker.authservice.shared.infrastructure.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class MessagingConfig {
    public static final String EVENTS_EXCHANGE = "poker.events";

    @Bean
    TopicExchange pokerEventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }
}
