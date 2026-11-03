package com.msspoker.authservice.dto.admin.response;

import java.util.List;

public record AccountBanHistoryResponse(List<AccountBanEventResponse> content, int page, int size,
        long totalElements, int totalPages) {
}
