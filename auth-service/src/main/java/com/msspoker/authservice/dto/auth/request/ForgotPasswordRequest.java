package com.msspoker.authservice.dto.auth.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForgotPasswordRequest(
        @Schema(description = "Email tài khoản đã xác thực; BE chuẩn hóa lowercase.", example = "khanh@example.com")
        @NotBlank @Email @Size(max = 254) String email
) {
}
