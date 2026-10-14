package com.msspoker.rankingservice.service.impl.event;

import com.msspoker.rankingservice.constant.RankingEventConstants;
import com.msspoker.rankingservice.exception.RankingExceptions;
import com.msspoker.rankingservice.repository.OutboxRepository;
import com.msspoker.rankingservice.service.event.OutboxPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
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
@ConditionalOnProperty(name = "ranking.events.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisherImpl implements OutboxPublisher {
    private final OutboxRepository repository;
    private final RabbitTemplate rabbit;
    private final ReentrantLock publishing = new ReentrantLock();

    @Scheduled(fixedRateString = "${ranking.events.poll-millis:2000}")
    @Override
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
                    rabbit.send(RankingEventConstants.EXCHANGE, event.getEventType(),
                            new Message(event.getBody().getBytes(StandardCharsets.UTF_8), properties), correlation);
                    var confirm = correlation.getFuture().get(RankingEventConstants.CONFIRM_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                    if (!confirm.ack() || correlation.getReturned() != null) throw RankingExceptions.eventDeliveryFailed();
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
