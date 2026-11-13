package com.msspoker.matchmakingservice.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PartyEventConfig {
    @Bean
    TopicExchange partyEventsExchange() { return new TopicExchange("poker.events", true, false); }
}
