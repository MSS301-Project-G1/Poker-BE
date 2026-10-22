package com.msspoker.socialservice.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.*;

@Configuration
public class EventConfig {
  @Bean
  Declarables eventTopology() {
    var exchange = new TopicExchange("poker.events", true, false);
    var all = new java.util.ArrayList<Declarable>();
    all.add(exchange);
    var notificationQueue =
        QueueBuilder.durable("social-service.notification.requested")
            .deadLetterExchange("")
            .deadLetterRoutingKey("social-service.notification.requested.dlq")
            .build();
    all.add(notificationQueue);
    all.add(new Queue("social-service.notification.requested.dlq", true));
    all.add(BindingBuilder.bind(notificationQueue).to(exchange).with("notification.requested"));
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
