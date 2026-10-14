package com.msspoker.competitionservice.service;

import com.msspoker.competitionservice.dto.event.MatchFinishedEvent;
import com.msspoker.competitionservice.dto.event.PlacementEvent;
import com.msspoker.competitionservice.dto.request.CreateRoomRequest;
import com.msspoker.competitionservice.entity.OutboxEntity;
import com.msspoker.competitionservice.enums.GameMode;
import com.msspoker.competitionservice.enums.RoomVisibility;
import com.msspoker.competitionservice.repository.CustomRoomRepository;
import com.msspoker.competitionservice.repository.OutboxRepository;
import com.msspoker.competitionservice.repository.RoomMemberRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/** Shared fixtures for room tests that run against the test H2 database. */
public final class RoomTestSupport {
    private RoomTestSupport() {
    }

    public static void clean(OutboxRepository outbox, RoomMemberRepository members, CustomRoomRepository rooms) {
        outbox.deleteAll();
        members.deleteAll();
        rooms.deleteAll();
    }

    public static CreateRoomRequest room(RoomVisibility visibility, String password) {
        return new CreateRoomRequest(visibility, password, 6, 10, 20);
    }

    public static MatchFinishedEvent finished(GameMode mode, UUID sourceId, List<UUID> players) {
        List<PlacementEvent> placements = IntStream.range(0, players.size())
                .mapToObj(i -> new PlacementEvent(players.get(i), i + 1, 0)).toList();
        return new MatchFinishedEvent(UUID.randomUUID(), UUID.randomUUID(), mode, sourceId, 0, placements,
                Instant.now().minusSeconds(60), Instant.now());
    }

    /** notification.requested payloads in the order they were written. */
    public static List<JsonNode> notifications(OutboxRepository outbox, JsonMapper json) {
        return outbox.findAll().stream().sorted(Comparator.comparing(OutboxEntity::getOccurredAt)
                        .thenComparing(OutboxEntity::getId))
                .map(row -> json.readTree(row.getBody()).get("payload")).toList();
    }

    public static List<String> recipients(JsonNode notification) {
        return notification.get("accountIds").valueStream().map(JsonNode::asString).toList();
    }
}
