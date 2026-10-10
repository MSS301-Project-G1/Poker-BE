package com.msspoker.authservice.dto.auth.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record VerifyRegistrationOtpRequest(
        @Schema(description = "Email đang chờ xác thực.", example = "khanh@example.com")
        @NotBlank @Email @Size(max = 254) String email,
        @Schema(description = "Mã trong mail, là string đúng 6 chữ số, giữ số 0 đầu. Thay ví dụ bằng mã vừa nhận.",
                example = "012345", minLength = 6, maxLength = 6)
        @NotBlank @Pattern(regexp = "[0-9]{6}") String otp
) {
}
