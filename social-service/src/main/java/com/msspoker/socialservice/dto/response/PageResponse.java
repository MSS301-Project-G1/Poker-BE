package com.msspoker.socialservice.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PageResponse<T>(
      List<T> items, int page, int size, long totalElements, int totalPages) {}
