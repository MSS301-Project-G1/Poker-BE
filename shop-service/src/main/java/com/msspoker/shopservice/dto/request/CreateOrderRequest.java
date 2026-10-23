package com.msspoker.shopservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Yêu cầu tạo đơn hàng thanh toán vật phẩm qua PayOS")
public record CreateOrderRequest(
        @Schema(description = "UUID của sản phẩm cần mua", example = "019488a0-0000-7000-8000-000000000001", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "ID sản phẩm không được để trống")
        UUID productId
) {}
