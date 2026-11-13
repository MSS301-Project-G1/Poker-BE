package com.msspoker.chatservice.dto.response;

import java.util.List;

public record MessageHistoryResponse(List<MessageResponse> items, String nextCursor) {}
