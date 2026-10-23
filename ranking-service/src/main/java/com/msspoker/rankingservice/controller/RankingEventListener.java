package com.msspoker.rankingservice.controller;

import com.msspoker.common.event.AccountRegisteredEvent;
import com.msspoker.common.event.EventEnvelope;
import com.msspoker.rankingservice.constant.RankingEventConstants;
import com.msspoker.rankingservice.dto.event.MatchFinishedEvent;
import com.msspoker.rankingservice.service.RatingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * RabbitMQ entry point. Malformed or invalid events are logged and acknowledged; database
 * failures propagate so the listener retries. Redelivery is safe because the handlers are idempotent.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ranking.events.enabled", havingValue = "true", matchIfMissing = true)
public class RankingEventListener {
    private final RatingService rating;
    private final JsonMapper json;

    @RabbitListener(queues = RankingEventConstants.ACCOUNT_REGISTERED_QUEUE)
    public void accountRegistered(Message message) {
        EventEnvelope<AccountRegisteredEvent> event = read(message, new TypeReference<EventEnvelope<AccountRegisteredEvent>>() {
        });
        if (event == null) return;
        try {
            rating.accountRegistered(event.payload());
        } catch (IllegalArgumentException ex) {
            log.warn("Dropping invalid {} event {}: {}", event.eventType(), event.eventId(), ex.getMessage());
        }
    }

    @RabbitListener(queues = RankingEventConstants.MATCH_FINISHED_QUEUE)
    public void matchFinished(Message message) {
        EventEnvelope<MatchFinishedEvent> event = read(message, new TypeReference<EventEnvelope<MatchFinishedEvent>>() {
        });
        if (event == null) return;
        try {
            rating.matchFinished(event.payload());
        } catch (IllegalArgumentException ex) {
            log.warn("Dropping invalid {} event {}: {}", event.eventType(), event.eventId(), ex.getMessage());
        }
    }

    private <T> EventEnvelope<T> read(Message message, TypeReference<EventEnvelope<T>> type) {
        try {
            return json.readValue(message.getBody(), type);
        } catch (JacksonException ex) {
            log.warn("Dropping unreadable event {}", message.getMessageProperties().getMessageId(), ex);
            return null;
        }
    }
}
