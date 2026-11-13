package com.msspoker.chatservice.dto.response;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        UUID conversationId,
        UUID senderId,
        String messageType,
        String content,
        UUID clientMessageId,
        Instant createdAt) {}
