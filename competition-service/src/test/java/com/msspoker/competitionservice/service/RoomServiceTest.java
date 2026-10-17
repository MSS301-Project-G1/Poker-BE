package com.msspoker.competitionservice.service;

import com.msspoker.competitionservice.dto.request.CreateRoomRequest;
import com.msspoker.competitionservice.dto.request.CreateTableRequest;
import com.msspoker.competitionservice.dto.response.FriendshipResponse;
import com.msspoker.competitionservice.dto.response.RoomResponse;
import com.msspoker.competitionservice.dto.response.TableCreatedResponse;
import com.msspoker.competitionservice.enums.CompetitionError;
import com.msspoker.competitionservice.enums.GameMode;
import com.msspoker.competitionservice.enums.RoomStatus;
import com.msspoker.competitionservice.enums.RoomVisibility;
import com.msspoker.competitionservice.exception.CompetitionApiException;
import com.msspoker.competitionservice.exception.CompetitionExceptions;
import com.msspoker.competitionservice.repository.CustomRoomRepository;
import com.msspoker.competitionservice.repository.OutboxRepository;
import com.msspoker.competitionservice.repository.RoomMemberRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static com.msspoker.competitionservice.service.RoomTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class RoomServiceTest {
    @Autowired RoomService rooms;
    @Autowired CustomRoomRepository roomRepository;
    @Autowired RoomMemberRepository memberRepository;
    @Autowired OutboxRepository outbox;
    @Autowired JsonMapper json;
    @MockitoBean GameClient game;
    @MockitoBean SocialClient social;

    final UUID owner = UUID.randomUUID();
    final UUID second = UUID.randomUUID();
    final UUID third = UUID.randomUUID();

    @BeforeEach
    void setup() {
        clean(outbox, memberRepository, roomRepository);
        when(social.friendship(any(), any())).thenReturn(new FriendshipResponse(true, false));
        when(game.createTable(any())).thenAnswer(call -> new TableCreatedResponse(UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void createIssuesReadableCodeAndOneOpenRoomPerPlayer() {
        RoomResponse room = rooms.create(owner, room(RoomVisibility.PUBLIC, "secret1"));

        assertTrue(room.roomCode().matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}"));
        assertEquals(owner, room.ownerId());
        assertEquals(RoomStatus.WAITING, room.status());
        assertTrue(room.hasPassword());
        assertEquals(1, room.members().size());
        assertTrue(room.members().getFirst().owner());
        assertNotEquals("secret1", roomRepository.findById(room.id()).orElseThrow().getPasswordHash());
        assertError(CompetitionError.COMP_ALREADY_IN_ROOM, () -> rooms.create(owner, room(RoomVisibility.PRIVATE, null)));
        assertError(CompetitionError.COMP_INVALID_REQUEST,
                () -> rooms.create(second, new CreateRoomRequest(RoomVisibility.PUBLIC, null, 7, 10, 20)));
        assertError(CompetitionError.COMP_INVALID_REQUEST,
                () -> rooms.create(second, new CreateRoomRequest(RoomVisibility.PUBLIC, null, 6, 600, 1200)));
    }

    @Test
    void joinChecksPasswordCapacityAndOtherRooms() {
        String code = rooms.create(owner, new CreateRoomRequest(RoomVisibility.PRIVATE, "secret1", 2, 10, 20)).roomCode();

        assertError(CompetitionError.COMP_WRONG_PASSWORD, () -> rooms.join(code, second, null));
        assertError(CompetitionError.COMP_WRONG_PASSWORD, () -> rooms.join(code, second, "wrong"));
        RoomResponse joined = rooms.join(code.toLowerCase(), second, "secret1");
        assertEquals(List.of(owner, second), joined.members().stream().map(m -> m.accountId()).toList());
        assertEquals(2, rooms.join(code, second, "ignored on rejoin").members().size());
        assertError(CompetitionError.COMP_ROOM_FULL, () -> rooms.join(code, third, "secret1"));
        assertError(CompetitionError.COMP_NOT_FOUND, () -> rooms.join("ZZZZZZ", third, null));

        String other = rooms.create(third, room(RoomVisibility.PUBLIC, null)).roomCode();
        assertError(CompetitionError.COMP_ALREADY_IN_ROOM, () -> rooms.join(other, second, null));

        JsonNode update = notifications(outbox, json).getLast();
        assertEquals("ROOM_UPDATED", update.get("type").asString());
        assertEquals("JOINED", update.get("data").get("change").asString());
        assertEquals(List.of(owner.toString(), second.toString()), recipients(update));
    }

    @Test
    void ownerLeavingPassesRoomToLongestWaitingAndLastLeaverClosesIt() {
        String code = rooms.create(owner, room(RoomVisibility.PUBLIC, null)).roomCode();
        rooms.join(code, second, null);
        rooms.join(code, third, null);

        rooms.leave(code, owner);
        assertEquals(second, rooms.get(code, second).ownerId());
        assertError(CompetitionError.COMP_FORBIDDEN, () -> rooms.get(code, owner));

        rooms.leave(code, second);
        rooms.leave(code, third);
        assertError(CompetitionError.COMP_NOT_FOUND, () -> rooms.get(code, third));
        assertTrue(rooms.mine(third).isEmpty());
        assertNotNull(rooms.create(third, room(RoomVisibility.PUBLIC, null)).roomCode());
    }

    @Test
    void onlyOwnerKicksAndTransfers() {
        String code = rooms.create(owner, room(RoomVisibility.PUBLIC, null)).roomCode();
        rooms.join(code, second, null);
        rooms.join(code, third, null);

        assertError(CompetitionError.COMP_FORBIDDEN, () -> rooms.kick(code, second, third));
        assertError(CompetitionError.COMP_INVALID_REQUEST, () -> rooms.kick(code, owner, owner));
        assertError(CompetitionError.COMP_INVALID_REQUEST, () -> rooms.kick(code, owner, UUID.randomUUID()));
        RoomResponse afterKick = rooms.kick(code, owner, third);
        assertEquals(2, afterKick.members().size());
        assertTrue(recipients(notifications(outbox, json).getLast()).contains(third.toString()));

        assertError(CompetitionError.COMP_FORBIDDEN, () -> rooms.transferOwner(code, second, second));
        RoomResponse transferred = rooms.transferOwner(code, owner, second);
        assertEquals(second, transferred.ownerId());
        assertTrue(transferred.members().stream().anyMatch(m -> m.accountId().equals(second) && m.owner()));
    }

    @Test
    void invitesNotifyFriendsOnly() {
        String code = rooms.create(owner, room(RoomVisibility.PRIVATE, "secret1")).roomCode();
        rooms.invite(code, owner, second);

        JsonNode invite = notifications(outbox, json).getLast();
        assertEquals("ROOM_INVITE", invite.get("type").asString());
        assertEquals(List.of(second.toString()), recipients(invite));
        assertEquals(code, invite.get("data").get("roomCode").asString());
        assertTrue(invite.get("data").get("hasPassword").asBoolean());

        when(social.friendship(owner, third)).thenReturn(new FriendshipResponse(true, true));
        assertError(CompetitionError.COMP_FORBIDDEN, () -> rooms.invite(code, owner, third));
        assertError(CompetitionError.COMP_FORBIDDEN, () -> rooms.invite(code, third, second));
        assertError(CompetitionError.COMP_INVALID_REQUEST, () -> rooms.invite(code, owner, owner));
        rooms.join(code, second, "secret1");
        assertError(CompetitionError.COMP_CONFLICT, () -> rooms.invite(code, owner, second));
    }

    @Test
    void startOpensCustomTableAndFinishedMatchReturnsRoomToWaiting() {
        String code = rooms.create(owner, room(RoomVisibility.PUBLIC, null)).roomCode();
        assertError(CompetitionError.COMP_CONFLICT, () -> rooms.start(code, owner));
        rooms.join(code, second, null);
        rooms.join(code, third, null);
        assertError(CompetitionError.COMP_FORBIDDEN, () -> rooms.start(code, second));
        rooms.ready(code, second, true);
        assertError(CompetitionError.COMP_CONFLICT, () -> rooms.start(code, owner));
        rooms.ready(code, third, true);

        RoomResponse started = rooms.start(code, owner);

        ArgumentCaptor<CreateTableRequest> sent = ArgumentCaptor.forClass(CreateTableRequest.class);
        verify(game).createTable(sent.capture());
        CreateTableRequest request = sent.getValue();
        assertEquals(GameMode.CUSTOM, request.mode());
        assertEquals(0, request.entryFee());
        assertEquals(List.of(owner, second, third), request.playerIds());
        assertEquals(10, request.settings().smallBlind());
        assertEquals(20, request.settings().bigBlind());
        assertEquals(1000, request.settings().startingChips());
        assertEquals(20, request.settings().turnTimeSeconds());
        assertEquals(RoomStatus.PLAYING, started.status());
        assertNotNull(started.tableId());

        JsonNode startedNotice = notifications(outbox, json).getLast();
        assertEquals("ROOM_STARTED", startedNotice.get("type").asString());
        assertEquals(started.tableId().toString(), startedNotice.get("data").get("tableId").asString());
        assertEquals(3, recipients(startedNotice).size());

        assertError(CompetitionError.COMP_CONFLICT, () -> rooms.leave(code, second));
        assertError(CompetitionError.COMP_CONFLICT, () -> rooms.ready(code, second, false));
        assertError(CompetitionError.COMP_CONFLICT, () -> rooms.join(code, UUID.randomUUID(), null));

        rooms.matchFinished(finished(GameMode.CUSTOM, request.sourceId(), request.playerIds()));
        RoomResponse waiting = rooms.get(code, owner);
        assertEquals(RoomStatus.WAITING, waiting.status());
        assertNull(waiting.tableId());
        assertTrue(waiting.members().stream().noneMatch(m -> m.ready()));
        assertEquals("MATCH_FINISHED", notifications(outbox, json).getLast().get("data").get("change").asString());

        long notices = outbox.count();
        rooms.matchFinished(finished(GameMode.CUSTOM, request.sourceId(), request.playerIds()));
        assertEquals(notices, outbox.count());

        rooms.ready(code, second, true);
        rooms.ready(code, third, true);
        rooms.start(code, owner);
        verify(game, times(2)).createTable(sent.capture());
        assertNotEquals(request.sourceId(), sent.getValue().sourceId());
    }

    @Test
    void failedStartKeepsSourceForRetryAndConflictIssuesNewSource() {
        String code = rooms.create(owner, room(RoomVisibility.PUBLIC, null)).roomCode();
        rooms.join(code, second, null);
        rooms.ready(code, second, true);

        when(game.createTable(any())).thenThrow(CompetitionExceptions.playerBusy());
        assertError(CompetitionError.COMP_PLAYER_BUSY, () -> rooms.start(code, owner));
        assertEquals(RoomStatus.WAITING, rooms.get(code, owner).status());
        UUID kept = roomRepository.findByRoomCodeAndIsDeletedFalse(code).orElseThrow().getCurrentSourceId();
        assertNotNull(kept);

        reset(game);
        TableCreatedResponse table = new TableCreatedResponse(UUID.randomUUID(), UUID.randomUUID());
        doThrow(CompetitionExceptions.sourceConflict()).when(game).createTable(argThat(r -> r != null && kept.equals(r.sourceId())));
        doReturn(table).when(game).createTable(argThat(r -> r != null && !kept.equals(r.sourceId())));

        RoomResponse started = rooms.start(code, owner);
        assertEquals(table.tableId(), started.tableId());
        verify(game).createTable(argThat(r -> kept.equals(r.sourceId())));
        verify(game).createTable(argThat(r -> !kept.equals(r.sourceId())));
        assertNotEquals(kept, roomRepository.findByRoomCodeAndIsDeletedFalse(code).orElseThrow().getCurrentSourceId());
    }

    @Test
    void otherModesUnknownSourcesAndInvalidEventsLeaveRoomsAlone() {
        String code = rooms.create(owner, room(RoomVisibility.PUBLIC, null)).roomCode();
        rooms.join(code, second, null);
        rooms.ready(code, second, true);
        UUID source = UUID.randomUUID();
        when(game.createTable(any())).thenReturn(new TableCreatedResponse(UUID.randomUUID(), UUID.randomUUID()));
        rooms.start(code, owner);
        UUID current = roomRepository.findByRoomCodeAndIsDeletedFalse(code).orElseThrow().getCurrentSourceId();

        rooms.matchFinished(finished(GameMode.RANK, current, List.of(owner, second)));
        rooms.matchFinished(finished(GameMode.CUSTOM, source, List.of(owner, second)));
        assertEquals(RoomStatus.PLAYING, rooms.get(code, owner).status());
        assertThrows(IllegalArgumentException.class, () -> rooms.matchFinished(finished(GameMode.CUSTOM, null, List.of())));
    }

    @Test
    void publicListShowsWaitingPublicRoomsNewestFirst() {
        String first = rooms.create(owner, room(RoomVisibility.PUBLIC, null)).roomCode();
        rooms.create(second, room(RoomVisibility.PRIVATE, null));
        String latest = rooms.create(third, room(RoomVisibility.PUBLIC, "secret1")).roomCode();
        rooms.join(first, UUID.randomUUID(), null);
        // Rooms created within one millisecond would otherwise tie on createdAt.
        var older = roomRepository.findByRoomCodeAndIsDeletedFalse(first).orElseThrow();
        older.setCreatedAt(older.getCreatedAt().minusSeconds(60));
        roomRepository.save(older);

        var page = rooms.publicRooms(0, 10);
        assertEquals(List.of(latest, first), page.content().stream().map(r -> r.roomCode()).toList());
        assertEquals(2, page.content().getLast().playerCount());
        assertTrue(page.content().getFirst().hasPassword());
        assertEquals(2, page.totalElements());
    }

    private void assertError(CompetitionError code, Executable action) {
        CompetitionApiException ex = assertThrows(CompetitionApiException.class, action);
        assertEquals(code, ex.getCode());
    }
}
