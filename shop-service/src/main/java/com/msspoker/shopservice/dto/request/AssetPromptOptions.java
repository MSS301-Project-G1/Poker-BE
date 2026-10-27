package com.msspoker.shopservice.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record AssetPromptOptions(
        UUID presetId,
        String presetName,
        String primaryColor,
        String secondaryColor,
        String accentColor,
        List<String> moodTags,

        @Size(max = 500, message = "Miêu tả bổ sung không được vượt quá 500 ký tự")
        String extraDescription,

        @Size(max = 200, message = "Yếu tố cần tránh không được vượt quá 200 ký tự")
        String avoidElements,

        Integer sourceFidelity, // 0 - 100

        UUID modelConfigId
) {}
