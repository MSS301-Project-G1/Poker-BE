package com.msspoker.shopservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ScheduleDeletionRequest(
        Integer durationHours,

        @Min(value = 1, message = "Thời gian đếm ngược tối thiểu là 1 phút")
        Integer durationMinutes,

        @NotBlank(message = "Vui lòng nhập lý do xóa và thông báo cho người chơi")
        String notice
) {
    public int getEffectiveMinutes() {
        if (durationMinutes != null && durationMinutes > 0) {
            return durationMinutes;
        }
        if (durationHours != null && durationHours > 0) {
            return durationHours * 60;
        }
        return 1;
    }
}

