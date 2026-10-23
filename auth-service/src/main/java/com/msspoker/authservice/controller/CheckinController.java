package com.msspoker.authservice.controller;

import com.msspoker.authservice.dto.checkin.response.CheckinCalendarResponse;
import com.msspoker.authservice.dto.checkin.response.CheckinResponse;
import com.msspoker.authservice.service.CheckinService;
import com.msspoker.authservice.shared.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/checkin")
@Tag(name = "Điểm danh", description = "Ngày theo giờ Việt Nam, mặc định thưởng 50 xu; chưa có điểm danh bù.")
public class CheckinController {
    private final CheckinService checkinService;

    @PostMapping
    @SecurityRequirement(name = "csrfToken")
    @Operation(summary = "Điểm danh hôm nay", description = "Cần access cookie và CSRF, không có body. "
            + "201 khi đã ghi nhận điểm danh; gọi lại trong cùng ngày trả 409. "
            + "Theo dõi rewardStatus qua lịch tháng; job tự thử lại phần thưởng đang chờ.")
    public ResponseEntity<ApiResponse<CheckinResponse>> checkIn(HttpServletRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<CheckinResponse>builder()
                        .code(ApiResponse.SUCCESS_CODE)
                        .message("Ghi nhận điểm danh thành công.")
                        .result(checkinService.checkIn(UUID.fromString(jwt.getSubject())))
                        .path(request.getRequestURI())
                        .timestamp(Instant.now())
                        .build()
        );
    }

    @GetMapping("/calendar")
    @Operation(summary = "Lịch điểm danh theo tháng", description = "Cần access cookie, không cần CSRF. "
            + "month bắt buộc dạng yyyy-MM; days chỉ gồm các ngày đã điểm danh, có trạng thái thưởng PENDING/REWARDED. "
            + "checkedInToday phản ánh hôm nay theo giờ Việt Nam, độc lập với tháng đang xem.")
    public ResponseEntity<ApiResponse<CheckinCalendarResponse>> getCalendar(HttpServletRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(example = "2026-10") @RequestParam String month) {
        return ResponseEntity.ok(
                ApiResponse.<CheckinCalendarResponse>builder()
                        .code(ApiResponse.SUCCESS_CODE)
                        .message("Lấy lịch điểm danh thành công.")
                        .result(checkinService.getCalendar(UUID.fromString(jwt.getSubject()), month))
                        .path(request.getRequestURI())
                        .timestamp(Instant.now())
                        .build()
        );
    }
}
