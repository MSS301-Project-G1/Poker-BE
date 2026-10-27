package com.msspoker.shopservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.util.List;

@Builder
public record UpdateAiModelConfigRequest(
        @NotBlank(message = "Tên hiển thị không được để trống")
        String displayName,

        List<String> capabilities,

        Boolean isActive,

        Boolean isDefault
) {}
