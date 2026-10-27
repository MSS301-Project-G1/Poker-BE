package com.msspoker.shopservice.dto.response;

import com.msspoker.shopservice.enums.AiModelCapability;
import com.msspoker.shopservice.enums.AiProviderType;
import lombok.Builder;

import java.util.List;

@Builder
public record AiModelDescriptor(
        AiProviderType provider,
        String modelName,
        String displayName,
        String description,
        List<AiModelCapability> capabilities
) {}
