package com.msspoker.authservice.dto.auth.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @Schema(description = "Mật khẩu hiện tại; BE đối chiếu với account trong access cookie.",
                format = "password", example = "OldPoker123!", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Size(max = 72) String currentPassword,
        @Schema(description = "Mật khẩu mới: ít nhất 8 Unicode code point, tối đa 72 byte UTF-8; không trim.",
                format = "password", example = "NewPoker123!", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Size(min = 8, max = 72) String newPassword,
        @Schema(description = "Phải khớp chính xác newPassword; BE và FE đều kiểm tra.",
                format = "password", example = "NewPoker123!", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Size(max = 72) String confirmPassword
) {
}
