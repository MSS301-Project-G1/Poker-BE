package com.msspoker.authservice.dto.profile.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmAvatarRequest(
        @Schema(description = "Key BE trả khi xin presign; gửi sau khi PUT S3 thành công.")
        @NotBlank @Size(max = 1024) String key) {
}
