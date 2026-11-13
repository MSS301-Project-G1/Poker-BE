package com.msspoker.socialservice.service.impl.event;

import com.msspoker.socialservice.repository.OutboxRepository;
import com.msspoker.socialservice.service.event.OutboxPublisher;

import lombok.RequiredArgsConstructor;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "social.events.publisher-enabled",
        havingValue = "true",
        matchIfMissing = true)
@Component
@RequiredArgsConstructor
public class OutboxPublisherImpl implements OutboxPublisher {
    private final OutboxRepository outbox;
    private final RabbitTemplate rabbit;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${social.events.poll-millis:2000}")
    @Transactional
    @Override
    public void publish() {
        for (var event : outbox.pendingLocked()) {
            try {
                var p = new MessageProperties();
                p.setContentType("application/json");
                p.setMessageId(event.getId().toString());
                p.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                var c = new CorrelationData(event.getId().toString());
                rabbit.send(
                        "poker.events",
                        "notification.requested",
                        new Message(event.getBody().getBytes(StandardCharsets.UTF_8), p),
                        c);
                if (!c.getFuture().get(5, TimeUnit.SECONDS).ack() || c.getReturned() != null)
                    throw new IllegalStateException("Broker did not route/confirm");
                event.setPublishedAt(clock.instant());
                event.setLastError(null);
            } catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                event.setAttempts(event.getAttempts() + 1);
                event.setLastError("Publish unconfirmed");
                event.setNextAttemptAt(
                        clock.instant()
                                .plusSeconds(
                                        Math.min(300, 1L << Math.min(8, event.getAttempts()))
                                                + ThreadLocalRandom.current().nextInt(3)));
            }
        }
    }
}
