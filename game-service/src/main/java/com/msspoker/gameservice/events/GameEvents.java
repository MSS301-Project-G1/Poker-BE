package com.msspoker.gameservice.events;

import com.msspoker.gameservice.api.GameDtos.Placement;
import com.msspoker.gameservice.engine.GameMode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class GameEvents {
    public static final String EXCHANGE = "poker.events";
    public static final String STARTED = "match.started";
    public static final String FINISHED = "match.finished";
    public static final int CONFIRM_TIMEOUT_MILLIS = 5000;

    private GameEvents() {
    }

    public record Envelope(UUID eventId, String eventType, Instant occurredAt, Object payload) {
    }

    public record Started(UUID matchId, UUID tableId, GameMode mode, UUID sourceId, List<UUID> playerIds) {
    }

    public record Finished(UUID matchId, UUID tableId, GameMode mode, UUID sourceId, long entryFee,
                           List<Placement> placements, Instant startedAt, Instant finishedAt) {
    }
}
