package com.msspoker.competitionservice.config;

import com.msspoker.competitionservice.constant.CompetitionEventConstants;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "competition.events.enabled", havingValue = "true", matchIfMissing = true)
public class EventConfig {
    @Bean
    public TopicExchange pokerEvents() {
        return new TopicExchange(CompetitionEventConstants.EXCHANGE, true, false);
    }

    @Bean
    public Queue matchFinishedQueue() {
        return new Queue(CompetitionEventConstants.MATCH_FINISHED_QUEUE, true);
    }

    @Bean
    public Binding matchFinishedBinding(TopicExchange pokerEvents, Queue matchFinishedQueue) {
        return BindingBuilder.bind(matchFinishedQueue).to(pokerEvents).with(CompetitionEventConstants.MATCH_FINISHED);
    }
}
