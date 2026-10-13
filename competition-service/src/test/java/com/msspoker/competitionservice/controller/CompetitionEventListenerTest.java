package com.msspoker.competitionservice.controller;

import com.msspoker.competitionservice.dto.response.TableCreatedResponse;
import com.msspoker.competitionservice.enums.RoomStatus;
import com.msspoker.competitionservice.enums.RoomVisibility;
import com.msspoker.competitionservice.repository.CustomRoomRepository;
import com.msspoker.competitionservice.repository.OutboxRepository;
import com.msspoker.competitionservice.repository.RoomMemberRepository;
import com.msspoker.competitionservice.service.GameClient;
import com.msspoker.competitionservice.service.RoomService;
import com.msspoker.competitionservice.service.RoomTestSupport;
import com.msspoker.competitionservice.service.TournamentService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class CompetitionEventListenerTest {
    @Autowired RoomService rooms;
    @Autowired TournamentService tournaments;
    @Autowired JsonMapper json;
    @Autowired CustomRoomRepository roomRepository;
    @Autowired RoomMemberRepository members;
    @Autowired OutboxRepository outbox;
    @MockitoBean GameClient game;
    CompetitionEventListener listener;

    @BeforeEach
    void setup() {
        // The listener bean is disabled in tests to avoid a broker; exercise it directly.
        listener = new CompetitionEventListener(rooms, tournaments, json);
        RoomTestSupport.clean(outbox, members, roomRepository);
    }

    @Test
    void gameEnvelopeReturnsRoomToWaitingAndBadMessagesAreDropped() {
        UUID owner = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        String code = rooms.create(owner, RoomTestSupport.room(RoomVisibility.PUBLIC, null)).roomCode();
        rooms.join(code, guest, null);
        rooms.ready(code, guest, true);
        when(game.createTable(any())).thenReturn(new TableCreatedResponse(UUID.randomUUID(), UUID.randomUUID()));
        rooms.start(code, owner);
        UUID source = roomRepository.findByRoomCodeAndIsDeletedFalse(code).orElseThrow().getCurrentSourceId();

        assertDoesNotThrow(() -> listener.matchFinished(message("not json")));
        assertDoesNotThrow(() -> listener.matchFinished(message("""
                {"eventId":"%s","eventType":"match.finished","occurredAt":"2026-10-13T08:00:00Z","payload":{"mode":"CUSTOM"}}
                """.formatted(UUID.randomUUID()))));
        assertEquals(RoomStatus.PLAYING, rooms.get(code, owner).status());

        listener.matchFinished(message("""
                {"eventId":"%s","eventType":"match.finished","occurredAt":"2026-10-13T08:00:00Z",
                 "payload":{"matchId":"%s","tableId":"%s","mode":"CUSTOM","sourceId":"%s","entryFee":0,
                   "placements":[{"accountId":"%s","place":1,"finalChips":2000},{"accountId":"%s","place":2,"finalChips":0}],
                   "startedAt":"2026-10-13T07:50:00Z","finishedAt":"2026-10-13T08:00:00Z","extra":1}}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), source, owner, guest)));
        assertEquals(RoomStatus.WAITING, rooms.get(code, owner).status());
    }

    private Message message(String body) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setMessageId(UUID.randomUUID().toString());
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }
}
