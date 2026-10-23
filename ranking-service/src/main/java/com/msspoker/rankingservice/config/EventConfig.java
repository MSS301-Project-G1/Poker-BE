package com.msspoker.rankingservice.config;

import com.msspoker.rankingservice.constant.RankingEventConstants;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "ranking.events.enabled", havingValue = "true", matchIfMissing = true)
public class EventConfig {
    @Bean
    public TopicExchange pokerEvents() {
        return new TopicExchange(RankingEventConstants.EXCHANGE, true, false);
    }

    @Bean
    public Queue accountRegisteredQueue() {
        return new Queue(RankingEventConstants.ACCOUNT_REGISTERED_QUEUE, true);
    }

    @Bean
    public Queue matchFinishedQueue() {
        return new Queue(RankingEventConstants.MATCH_FINISHED_QUEUE, true);
    }

    @Bean
    public Binding accountRegisteredBinding(TopicExchange pokerEvents, Queue accountRegisteredQueue) {
        return BindingBuilder.bind(accountRegisteredQueue).to(pokerEvents).with(RankingEventConstants.ACCOUNT_REGISTERED);
    }

    @Bean
    public Binding matchFinishedBinding(TopicExchange pokerEvents, Queue matchFinishedQueue) {
        return BindingBuilder.bind(matchFinishedQueue).to(pokerEvents).with(RankingEventConstants.MATCH_FINISHED);
    }
}
