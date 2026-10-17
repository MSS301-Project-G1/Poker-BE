package com.msspoker.authservice.dto.profile.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SelectAvatarRequest(
        @Schema(description = "ID từ danh sách avatar có sẵn.", example = "spade")
        @NotBlank @Size(max = 30) String avatarId) {
}
