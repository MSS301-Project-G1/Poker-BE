package com.msspoker.authservice.dto.profile.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AvatarPresignRequest(
        @Schema(example = "avatar.png") @NotBlank @Size(max = 255) String fileName,
        @Schema(example = "image/png") @NotBlank @Size(max = 50) String contentType,
        @Schema(description = "File.size ở FE, tính bằng byte; BE kiểm tra lại ảnh trên S3 khi xác nhận.", example = "123456")
        @NotNull @Positive Long fileSize) {
}
