package com.msspoker.competitionservice.dto.response;

import java.util.List;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
}
