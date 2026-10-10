package com.msspoker.authservice.dto.auth.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record RegisterRequest(
        @Schema(description = "Email đăng ký; được chuẩn hóa lowercase tại service.", example = "khanh@example.com")
        @NotBlank @Email @Size(max = 254) String email,
        @Schema(description = "Ít nhất 8 ký tự Unicode, tối đa 72 byte UTF-8; không trim.",
                format = "password", example = "Poker123!", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Size(min = 8, max = 72) String password,
        @Schema(description = "Nhập lại chính xác password; BE tự kiểm tra khớp, không lưu field này.",
                format = "password", example = "Poker123!", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Size(max = 72) String confirmPassword,
        @Schema(description = "Tên hiển thị, được trùng; service bỏ khoảng trắng hai đầu.", example = "Khanh")
        @NotBlank @Size(max = 100) String displayName
) {
}
