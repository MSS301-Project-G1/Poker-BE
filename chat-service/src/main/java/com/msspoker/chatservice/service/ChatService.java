package com.msspoker.chatservice.service;

import java.util.*;
import com.msspoker.chatservice.dto.response.*;
import com.msspoker.chatservice.dto.request.SendMessageRequest;

public interface ChatService {
  record MessageCommitted(MessageResponse message, List<UUID> members) {}
  ConversationResponse direct(UUID actor, UUID target);
  void addMember(UUID conversation, UUID account);
  ConversationResponse get(UUID actor, UUID id);
  default PageResponse<ConversationResponse> list(UUID actor, int page, int size) {
    return list(actor, page, size, null);
  }
  PageResponse<ConversationResponse> list(UUID actor, int page, int size, String type);
  MessageResponse send(UUID actor, UUID id, SendMessageRequest request);
  MessageHistoryResponse history(UUID actor, UUID id, String before, int limit);
}
