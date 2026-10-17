package com.msspoker.shopservice.dto.response;

import com.msspoker.shopservice.enums.PaymentProvider;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record PaymentConfigResponse(
        UUID id,
        PaymentProvider provider,
        String name,
        String bankCode,
        String accountNumber,
        String accountHolder,
        boolean isActive,
        boolean isMain,
        Instant createdAt,
        Instant updatedAt
) {}
