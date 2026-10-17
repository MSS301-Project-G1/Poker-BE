package com.msspoker.shopservice.dto.response;

import lombok.Builder;

import java.util.List;

/**
 * Custom pagination wrapper (Rule #12).
 * Thay thế raw Page<T> của Spring Data.
 */
@Builder
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
    public static <T> PageResponse<T> of(org.springframework.data.domain.Page<T> springPage) {
        return new PageResponse<>(
                springPage.getContent(),
                springPage.getNumber(),
                springPage.getSize(),
                springPage.getTotalElements(),
                springPage.getTotalPages(),
                springPage.isLast()
        );
    }
}
