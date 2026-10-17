package com.msspoker.gameservice.dto.event;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope(UUID eventId, String eventType, Instant occurredAt, Object payload) {
}
