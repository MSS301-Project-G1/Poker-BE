package com.msspoker.socialservice.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FriendRequestResponse(
      UUID id,
      UUID requesterId,
      UUID addresseeId,
      String status,
      Instant createdAt,
      Instant updatedAt) {}
