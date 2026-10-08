package com.msspoker.gameservice.config;

import com.msspoker.gameservice.constant.GameEventConstants;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "game.events.enabled", havingValue = "true", matchIfMissing = true)
public class EventConfig {
    @Bean
    public TopicExchange pokerEvents() {
        return new TopicExchange(GameEventConstants.EXCHANGE, true, false);
    }
}
