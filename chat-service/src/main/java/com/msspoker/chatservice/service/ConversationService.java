package com.msspoker.chatservice.service;

import com.msspoker.chatservice.dto.response.ConversationResponse;
import com.msspoker.chatservice.dto.response.PageResponse;

import java.util.List;
import java.util.UUID;

public interface ConversationService {
    ConversationResponse direct(UUID actor, UUID target);
    ConversationResponse world(UUID actor);

    void addMember(UUID conversation, UUID account);

    List<UUID> memberIds(UUID conversation);

    ConversationResponse get(UUID actor, UUID id);

    default PageResponse<ConversationResponse> list(UUID actor, int page, int size) {
        return list(actor, page, size, null);
    }

    PageResponse<ConversationResponse> list(UUID actor, int page, int size, String type);
}
