package com.msspoker.shopservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ScheduleDeletionRequest(
        @Min(value = 1, message = "Thời gian đếm ngược tối thiểu là 1 giờ")
        int durationHours,

        @NotBlank(message = "Vui lòng nhập lý do xóa và thông báo cho người chơi")
        String notice
) {
}

