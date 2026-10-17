package com.msspoker.shopservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "shop.events";
    public static final String TOPUP_QUEUE_NAME = "wallet.topup.queue";
    public static final String TOPUP_ROUTING_KEY = "shop.order.topup.success";

    @Bean
    public TopicExchange shopEventsExchange() {
        return new TopicExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue walletTopupQueue() {
        return new Queue(TOPUP_QUEUE_NAME, true);
    }

    @Bean
    public Binding bindingTopupQueue(Queue walletTopupQueue, TopicExchange shopEventsExchange) {
        return BindingBuilder.bind(walletTopupQueue).to(shopEventsExchange).with(TOPUP_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
