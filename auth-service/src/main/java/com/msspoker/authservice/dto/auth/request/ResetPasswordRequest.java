package com.msspoker.authservice.dto.auth.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @Schema(description = "Email đã yêu cầu OTP đặt lại mật khẩu.", example = "khanh@example.com")
        @NotBlank @Email @Size(max = 254) String email,
        @Schema(description = "OTP đặt lại mật khẩu từ email, đúng 6 chữ số; không dùng OTP đăng ký.", example = "012345")
        @NotBlank @Pattern(regexp = "[0-9]{6}") String otp,
        @Schema(description = "Mật khẩu mới: ít nhất 8 ký tự Unicode, tối đa 72 byte UTF-8; không trim.",
                format = "password", example = "NewPoker123!", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Size(min = 8, max = 72) String password,
        @Schema(description = "Phải khớp chính xác password, kiểm tra tại BE và FE.",
                format = "password", example = "NewPoker123!", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Size(max = 72) String confirmPassword
) {
}
