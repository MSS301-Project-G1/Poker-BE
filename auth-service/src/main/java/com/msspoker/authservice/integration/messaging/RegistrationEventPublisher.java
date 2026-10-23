package com.msspoker.authservice.integration.messaging;

import com.msspoker.authservice.entity.RegistrationEvent;
import com.msspoker.authservice.shared.infrastructure.config.MessagingConfig;
import com.msspoker.common.event.AccountRegisteredEvent;
import com.msspoker.common.event.EventEnvelope;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RegistrationEventPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final JsonMapper jsonMapper;

    public void publish(RegistrationEvent event) throws Exception {
        EventEnvelope<AccountRegisteredEvent> envelope = new EventEnvelope<>(event.getEventId(),
                AccountRegisteredEvent.EVENT_TYPE, event.getOccurredAt(),
                new AccountRegisteredEvent(event.getAccountId()));
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(event.getEventId().toString());
        Message message = new Message(jsonMapper.writeValueAsBytes(envelope), properties);
        CorrelationData correlation = new CorrelationData(event.getEventId().toString());
        rabbitTemplate.send(MessagingConfig.EVENTS_EXCHANGE, AccountRegisteredEvent.EVENT_TYPE, message, correlation);
        CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
        // ACK alone also occurs for unroutable messages; require a queue to have accepted it.
        if (!confirm.ack() || correlation.getReturned() != null) {
            throw new IllegalStateException("Registration event was not routed and confirmed");
        }
    }
}
