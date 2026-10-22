package com.msspoker.chatservice.dto.response;

import java.time.Instant;
import java.util.*;

public final class ChatDtos {
  private ChatDtos() {}

  public record ConversationView(
      UUID id,
      String type,
      UUID refId,
      Instant closedAt,
      Instant createdAt,
      List<UUID> memberIds) {}

  public record MessageView(
      UUID id,
      UUID conversationId,
      UUID senderId,
      String messageType,
      String content,
      UUID clientMessageId,
      Instant createdAt) {}

  public record History(List<MessageView> items, String nextCursor) {}

  public record PageView<T>(
      List<T> items, int page, int size, long totalElements, int totalPages) {}
}
