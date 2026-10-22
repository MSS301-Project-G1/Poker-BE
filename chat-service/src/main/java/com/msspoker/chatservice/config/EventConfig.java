package com.msspoker.chatservice.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.*;

@Configuration
public class EventConfig {
  @Bean
  Declarables eventTopology() {
    var exchange = new TopicExchange("poker.events", true, false);
    var all = new java.util.ArrayList<Declarable>();
    all.add(exchange);
    var matchStartedQueue =
        QueueBuilder.durable("chat-service.match.started")
            .deadLetterExchange("")
            .deadLetterRoutingKey("chat-service.match.started.dlq")
            .build();
    all.add(matchStartedQueue);
    all.add(new Queue("chat-service.match.started.dlq", true));
    all.add(BindingBuilder.bind(matchStartedQueue).to(exchange).with("match.started"));
    var matchFinishedQueue =
        QueueBuilder.durable("chat-service.match.finished")
            .deadLetterExchange("")
            .deadLetterRoutingKey("chat-service.match.finished.dlq")
            .build();
    all.add(matchFinishedQueue);
    all.add(new Queue("chat-service.match.finished.dlq", true));
    all.add(BindingBuilder.bind(matchFinishedQueue).to(exchange).with("match.finished"));
    return new Declarables(all);
  }

  @Bean
  org.springframework.amqp.rabbit.retry.MessageRecoverer eventRecoverer() {
    return (message, cause) -> {
      org.slf4j.LoggerFactory.getLogger(EventConfig.class)
          .warn(
              "Event rejected after retries eventId={}",
              message.getMessageProperties().getMessageId());
      throw new org.springframework.amqp.AmqpRejectAndDontRequeueException(
          "Event failed after finite retries");
    };
  }
}
