package com.msspoker.shopservice.dto.response;

import lombok.Builder;

import java.util.UUID;

@Builder
public record OwnedSoundResponse(
        UUID productId,
        String soundUrl
) {}
