package com.msspoker.shopservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record UpdatePaymentConfigRequest(
        @NotBlank(message = "Tên phương thức không được để trống")
        String name,

        String bankCode,
        String accountNumber,
        String accountHolder
) {}
