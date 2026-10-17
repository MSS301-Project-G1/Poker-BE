package com.msspoker.shopservice.dto.response;

import lombok.Builder;

import java.util.UUID;

@Builder
public record CategoryResponse(
        UUID id,
        String code,
        String name
) {}
