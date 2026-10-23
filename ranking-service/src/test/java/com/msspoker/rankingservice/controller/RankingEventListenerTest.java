package com.msspoker.rankingservice.controller;

import com.msspoker.rankingservice.repository.EloHistoryRepository;
import com.msspoker.rankingservice.repository.PlayerEloRepository;
import com.msspoker.rankingservice.repository.RewardCreditRepository;
import com.msspoker.rankingservice.service.RatingService;
import com.msspoker.rankingservice.service.SeasonService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RankingEventListenerTest {
    @Autowired RatingService rating;
    @Autowired SeasonService seasons;
    @Autowired JsonMapper json;
    @Autowired PlayerEloRepository players;
    @Autowired EloHistoryRepository history;
    @Autowired RewardCreditRepository rewards;
    RankingEventListener listener;

    @BeforeEach
    void setup() {
        // The listener bean is disabled in tests to avoid a broker; exercise it directly.
        listener = new RankingEventListener(rating, json);
        rewards.deleteAll();
        history.deleteAll();
        players.deleteAll();
    }

    @Test
    void readsAuthAndGameEnvelopesIgnoringUnknownFields() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        listener.accountRegistered(message("""
                {"eventId":"%s","eventType":"account.registered","occurredAt":"2026-10-13T08:00:00Z",
                 "payload":{"accountId":"%s"},"futureField":true}
                """.formatted(UUID.randomUUID(), first)));
        assertTrue(players.findBySeasonIdAndAccountIdAndIsDeletedFalse(seasons.active().getId(), first).isPresent());

        UUID matchId = UUID.randomUUID();
        listener.matchFinished(message("""
                {"eventId":"%s","eventType":"match.finished","occurredAt":"2026-10-13T08:00:00Z",
                 "payload":{"matchId":"%s","tableId":"%s","mode":"RANK","sourceId":"%s","entryFee":100,
                   "placements":[{"accountId":"%s","place":1,"finalChips":2000,"seatIndex":0},
                                 {"accountId":"%s","place":2,"finalChips":0}],
                   "startedAt":"2026-10-13T07:50:00Z","finishedAt":"2026-10-13T08:00:00Z"}}
                """.formatted(UUID.randomUUID(), matchId, UUID.randomUUID(), UUID.randomUUID(), first, second)));

        assertEquals(20, players.findBySeasonIdAndAccountIdAndIsDeletedFalse(seasons.active().getId(), first)
                .orElseThrow().getCurrentElo());
        assertEquals(2, history.findByMatchIdOrderByPlace(matchId).size());
        assertEquals(2, rewards.findByMatchIdOrderByPlace(matchId).size());
    }

    @Test
    void malformedAndInvalidEventsAreDroppedWithoutRetry() {
        assertDoesNotThrow(() -> listener.matchFinished(message("not json")));
        assertDoesNotThrow(() -> listener.accountRegistered(message("""
                {"eventId":"%s","eventType":"account.registered","occurredAt":"2026-10-13T08:00:00Z","payload":{}}
                """.formatted(UUID.randomUUID()))));
        assertDoesNotThrow(() -> listener.matchFinished(message("""
                {"eventId":"%s","eventType":"match.finished","occurredAt":"2026-10-13T08:00:00Z",
                 "payload":{"matchId":"%s","mode":"RANK","entryFee":100,"placements":[]}}
                """.formatted(UUID.randomUUID(), UUID.randomUUID()))));
        assertEquals(0, players.count());
        assertEquals(0, history.count());
    }

    private Message message(String body) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setMessageId(UUID.randomUUID().toString());
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }
}
