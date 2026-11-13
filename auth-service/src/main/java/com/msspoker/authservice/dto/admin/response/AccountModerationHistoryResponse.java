package com.msspoker.authservice.dto.admin.response;

import java.util.List;

public record AccountModerationHistoryResponse(List<AccountModerationEventResponse> content, int page, int size,
        long totalElements, int totalPages) {
}
