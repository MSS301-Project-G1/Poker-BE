package com.msspoker.shopservice.dto.request;

import com.msspoker.shopservice.enums.AiProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.List;

@Builder
public record CreateAiModelConfigRequest(
        @NotNull(message = "Nhà cung cấp AI không được để trống")
        AiProviderType provider,

        @NotBlank(message = "Tên model không được để trống")
        String modelName,

        @NotBlank(message = "Tên hiển thị không được để trống")
        String displayName,

        List<String> capabilities,

        Boolean isActive,

        Boolean isDefault
) {}
