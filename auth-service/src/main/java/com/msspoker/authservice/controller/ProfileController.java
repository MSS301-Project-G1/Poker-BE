package com.msspoker.authservice.controller;

import com.msspoker.authservice.dto.profile.response.ProfileResponse;
import com.msspoker.authservice.service.ProfileService;
import com.msspoker.authservice.shared.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/profiles")
@Tag(name = "Hồ sơ", description = "Thông tin hồ sơ công khai cho người đã đăng nhập.")
public class ProfileController {
    private final ProfileService profileService;

    @GetMapping("/batch")
    @Operation(summary = "Lấy hồ sơ theo danh sách account ID", description = "Yêu cầu access cookie hợp lệ. "
            + "Nhận 1–100 UUID, phân cách bằng dấu phẩy hoặc lặp query ids. "
            + "Chỉ trả accountId, displayName và avatarUrl; bỏ ID trùng, giữ thứ tự xuất hiện đầu tiên. "
            + "Bỏ qua ID không tồn tại, chưa có hồ sơ hoặc tài khoản đã xóa; không tìm thấy trả danh sách rỗng.")
    public ResponseEntity<ApiResponse<List<ProfileResponse>>> getProfilesBatch(HttpServletRequest request,
            @Parameter(description = "Account ID cần lấy hồ sơ; tối đa 100 phần tử trước khi bỏ trùng.")
            @RequestParam("ids") List<UUID> ids) {
        return ResponseEntity.ok(ApiResponse.<List<ProfileResponse>>builder().code(ApiResponse.SUCCESS_CODE)
                .message("Lấy danh sách hồ sơ thành công.").result(profileService.getProfilesBatch(ids))
                .path(request.getRequestURI()).timestamp(Instant.now()).build());
    }
}
