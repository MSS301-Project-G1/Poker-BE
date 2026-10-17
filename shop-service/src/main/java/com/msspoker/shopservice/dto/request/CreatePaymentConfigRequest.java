package com.msspoker.shopservice.dto.request;

import com.msspoker.shopservice.enums.PaymentProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record CreatePaymentConfigRequest(
        @NotNull(message = "Provider không được để trống")
        PaymentProvider provider,

        @NotBlank(message = "Tên phương thức không được để trống")
        String name,

        String bankCode,
        String accountNumber,
        String accountHolder
) {}
