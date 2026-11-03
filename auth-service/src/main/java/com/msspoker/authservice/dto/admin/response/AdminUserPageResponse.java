package com.msspoker.authservice.dto.admin.response;

import java.util.List;

public record AdminUserPageResponse(List<AdminUserResponse> content, int page, int size,
        long totalElements, int totalPages) {
}
