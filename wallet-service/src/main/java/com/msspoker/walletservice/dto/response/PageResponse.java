package com.msspoker.walletservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Custom pagination wrapper (Rule #12).
 * Thay thế raw Page<T> của Spring Data —
 * chỉ giữ các trường cần thiết cho client.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean last;

    public static <T> PageResponse<T> of(org.springframework.data.domain.Page<T> springPage) {
        return PageResponse.<T>builder()
                .content(springPage.getContent())
                .page(springPage.getNumber())
                .size(springPage.getSize())
                .totalElements(springPage.getTotalElements())
                .totalPages(springPage.getTotalPages())
                .last(springPage.isLast())
                .build();
    }
}
