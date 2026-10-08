package com.msspoker.gameservice.events;

import com.msspoker.gameservice.persistence.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import com.msspoker.gameservice.exception.GameExceptions;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "game.events.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {
    private final OutboxRepository repository;
    private final RabbitTemplate rabbit;
    private final ReentrantLock publishing = new ReentrantLock();

    @Scheduled(fixedRateString = "${game.events.poll-millis:2000}")
    public void publishPending() {
        if (!publishing.tryLock()) return;
        try {
        for (var event : repository.findTop20ByPublishedAtIsNullOrderByOccurredAtAscIdAsc()) {
            try {
                MessageProperties properties = new MessageProperties();
                properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                properties.setMessageId(event.getId().toString());
                properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                CorrelationData correlation = new CorrelationData(event.getId().toString());
                rabbit.setMandatory(true);
                rabbit.send(GameEvents.EXCHANGE, event.getEventType(),
                        new Message(event.getBody().getBytes(StandardCharsets.UTF_8), properties), correlation);
                var confirm = correlation.getFuture().get(GameEvents.CONFIRM_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                if (!confirm.isAck() || correlation.getReturned() != null) throw GameExceptions.eventDeliveryFailed();
                event.setPublishedAt(Instant.now());
                repository.save(event);
            } catch (Exception ex) {
                log.warn("Outbox publish failed for event {}; will retry", event.getId(), ex);
                break;
            }
        }
        } finally {
            publishing.unlock();
        }
    }
}
