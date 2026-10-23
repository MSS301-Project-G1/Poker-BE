package com.msspoker.shopservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.UUID;

@Builder
public record GrantItemRequest(
        @NotNull(message = "accountId không được để trống")
        UUID accountId,

        @NotNull(message = "productId không được để trống")
        UUID productId,

        @NotBlank(message = "idempotencyKey không được để trống")
        String idempotencyKey
) {}
