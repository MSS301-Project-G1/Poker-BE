package com.msspoker.shopservice.dto.response;

import com.msspoker.shopservice.enums.AiProviderType;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record AiModelConfigResponse(
        UUID id,
        AiProviderType provider,
        String modelName,
        String displayName,
        List<String> capabilities,
        boolean isActive,
        boolean isDefault,
        Instant createdAt,
        Instant updatedAt
) {}
