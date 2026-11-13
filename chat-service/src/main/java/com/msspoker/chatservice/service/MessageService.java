package com.msspoker.chatservice.service;

import com.msspoker.chatservice.dto.request.SendMessageRequest;
import com.msspoker.chatservice.dto.response.MessageHistoryResponse;
import com.msspoker.chatservice.dto.response.MessageResponse;

import java.util.UUID;

public interface MessageService {
    MessageResponse send(UUID actor, UUID conversationId, SendMessageRequest request);

    MessageHistoryResponse history(UUID actor, UUID conversationId, String before, int limit);
}
