package com.msspoker.authservice.dto.profile.request;

import com.msspoker.authservice.enums.GenderEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateProfileRequest(
        @Schema(description = "Tên hiển thị bắt buộc, được trùng; service bỏ khoảng trắng hai đầu.", example = "Khanh")
        @NotBlank @Size(max = 100) String displayName,
        @Schema(example = "Nguyễn Khánh") @Size(max = 150) String fullName,
        @Schema(description = "7–15 chữ số, có thể bắt đầu bằng +; không xác thực sở hữu số điện thoại.", example = "0901234567")
        @Size(max = 20) String phoneNumber,
        GenderEnum gender,
        @Schema(description = "Ngày sinh yyyy-MM-dd, không sau ngày hiện tại theo giờ Việt Nam.", example = "2003-05-12")
        LocalDate birthDate,
        @Size(max = 500) String bio,
        @Schema(description = "Hai chữ cái; service chuyển thành chữ hoa.", example = "VN")
        @Size(max = 2) String countryCode,
        @Size(max = 100) String city
) {
}
