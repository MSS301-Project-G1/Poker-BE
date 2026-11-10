package com.msspoker.shopservice.dto.response;

import lombok.Builder;
import java.util.UUID;

@Builder
public record ProductSoundResponse(
        UUID productId,
        String soundUrl,
        String message
) {}
