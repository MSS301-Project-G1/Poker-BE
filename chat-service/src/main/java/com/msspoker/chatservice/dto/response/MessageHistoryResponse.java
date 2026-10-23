package com.msspoker.chatservice.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MessageHistoryResponse(List<MessageResponse> items, String nextCursor) {}
