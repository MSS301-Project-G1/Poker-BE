package com.msspoker.socialservice.dto.event;

import java.util.*;
import tools.jackson.databind.JsonNode;

public record Notification(
    List<UUID> accountIds, String type, String title, String body, JsonNode data) {
  private static final Set<String> TYPES =
      Set.of(
          "FRIEND_REQUEST",
          "FRIEND_ACCEPTED",
          "MATCH_FOUND",
          "MATCH_READY",
          "ROOM_INVITE",
          "ROOM_UPDATED",
          "ROOM_STARTED",
          "TOURNAMENT_INVITE",
          "TOURNAMENT_TABLE_READY",
          "SEASON_REWARD_AVAILABLE",
          "REPORT_RESOLVED",
          "PAYMENT_SUCCEEDED");

  public void validate() {
    if (accountIds == null
        || accountIds.isEmpty()
        || accountIds.size() > 1000
        || accountIds.stream().anyMatch(Objects::isNull)
        || type == null
        || !TYPES.contains(type)
        || title == null
        || title.isBlank()
        || title.length() > 200
        || body == null
        || body.length() > 4000
        || data == null
        || !data.isObject()) throw new IllegalArgumentException("Invalid notification payload");
  }
}
