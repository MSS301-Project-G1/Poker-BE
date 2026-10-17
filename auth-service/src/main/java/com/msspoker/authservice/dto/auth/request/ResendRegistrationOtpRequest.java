package com.msspoker.authservice.dto.auth.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record ResendRegistrationOtpRequest(
        @Schema(description = "Email đã đăng ký nhưng chưa hoàn tất xác thực.", example = "khanh@example.com")
        @NotBlank @Email @Size(max = 254) String email
) {
}
