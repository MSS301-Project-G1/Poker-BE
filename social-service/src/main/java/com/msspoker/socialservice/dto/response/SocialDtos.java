package com.msspoker.socialservice.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.*;

public final class SocialDtos {
  private SocialDtos() {}

  public record FriendRequestView(
      UUID id,
      UUID requesterId,
      UUID addresseeId,
      String status,
      Instant createdAt,
      Instant updatedAt) {}

  public record RelationView(
      @JsonProperty("isFriend") boolean isFriend, @JsonProperty("isBlocked") boolean isBlocked) {}

  public record PageView<T>(
      List<T> items, int page, int size, long totalElements, int totalPages) {}
}
