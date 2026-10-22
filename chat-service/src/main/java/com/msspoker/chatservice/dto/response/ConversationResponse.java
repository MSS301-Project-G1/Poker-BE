package com.msspoker.chatservice.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationResponse(
      UUID id,
      String type,
      UUID refId,
      Instant closedAt,
      Instant createdAt,
      List<UUID> memberIds) {}
