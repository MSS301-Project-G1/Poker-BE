package com.msspoker.authservice.dto.profile.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SelectAvatarRequest(
        @Schema(description = "UUID từ danh sách avatar do admin tạo.")
        @NotNull UUID avatarId) {
}
