package com.msspoker.competitionservice.controller;

import com.msspoker.common.event.EventEnvelope;
import com.msspoker.competitionservice.constant.CompetitionEventConstants;
import com.msspoker.competitionservice.dto.event.MatchFinishedEvent;
import com.msspoker.competitionservice.service.RoomService;
import com.msspoker.competitionservice.service.TournamentService;

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
@ConditionalOnProperty(name = "competition.events.enabled", havingValue = "true", matchIfMissing = true)
public class CompetitionEventListener {
    private final RoomService rooms;
    private final TournamentService tournaments;
    private final JsonMapper json;

    /** CUSTOM results go to rooms and TOURNAMENT results to tournaments; each ignores the other mode. */
    @RabbitListener(queues = CompetitionEventConstants.MATCH_FINISHED_QUEUE)
    public void matchFinished(Message message) {
        EventEnvelope<MatchFinishedEvent> event;
        try {
            event = json.readValue(message.getBody(), new TypeReference<EventEnvelope<MatchFinishedEvent>>() {
            });
        } catch (JacksonException ex) {
            log.warn("Dropping unreadable event {}", message.getMessageProperties().getMessageId(), ex);
            return;
        }
        try {
            rooms.matchFinished(event.payload());
            tournaments.matchFinished(event.payload());
        } catch (IllegalArgumentException ex) {
            log.warn("Dropping invalid {} event {}: {}", event.eventType(), event.eventId(), ex.getMessage());
        }
    }
}
