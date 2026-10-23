package com.msspoker.authservice.controller;

import com.msspoker.authservice.dto.profile.request.UpdateProfileRequest;
import com.msspoker.authservice.dto.profile.response.MyProfileResponse;
import com.msspoker.authservice.dto.profile.response.ProfileResponse;
import com.msspoker.authservice.service.ProfileService;
import com.msspoker.authservice.shared.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/profiles")
@Tag(name = "Hồ sơ", description = "Hồ sơ cá nhân và tên/avatar công khai; yêu cầu access cookie.")
public class ProfileController {
    private final ProfileService profileService;

    @GetMapping("/me")
    @Operation(summary = "Xem hồ sơ của mình", description = "Lấy account ID từ JWT trong access cookie, "
            + "không nhận ID từ client. Trả cả thông tin riêng của chủ tài khoản; không trả mật khẩu/token.")
    public ResponseEntity<ApiResponse<MyProfileResponse>> getMyProfile(HttpServletRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(
                ApiResponse.<MyProfileResponse>builder()
                        .code(ApiResponse.SUCCESS_CODE)
                        .message("Lấy hồ sơ cá nhân thành công.")
                        .result(profileService.getMyProfile(UUID.fromString(jwt.getSubject())))
                        .path(request.getRequestURI())
                        .timestamp(Instant.now())
                        .build()
        );
    }

    @PutMapping("/me")
    @SecurityRequirement(name = "csrfToken")
    @Operation(summary = "Cập nhật hồ sơ của mình", description = "Gửi toàn bộ form với displayName bắt buộc. "
            + "Field tùy chọn bị bỏ qua/null/chuỗi rỗng sẽ xóa giá trị; tên hiển thị được trùng. "
            + "Cần cookie và header CSRF; không thay email, avatar, role hoặc thông tin phiên.")
    public ResponseEntity<ApiResponse<MyProfileResponse>> updateMyProfile(HttpServletRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProfileRequest updateRequest) {
        return ResponseEntity.ok(
                ApiResponse.<MyProfileResponse>builder()
                        .code(ApiResponse.SUCCESS_CODE)
                        .message("Cập nhật hồ sơ cá nhân thành công.")
                        .result(profileService.updateMyProfile(UUID.fromString(jwt.getSubject()), updateRequest))
                        .path(request.getRequestURI())
                        .timestamp(Instant.now())
                        .build()
        );
    }

    @GetMapping("/batch")
    @Operation(summary = "Lấy hồ sơ theo danh sách account ID", description = "Yêu cầu access cookie hợp lệ. "
            + "Nhận 1–100 UUID, phân cách bằng dấu phẩy hoặc lặp query ids. "
            + "Chỉ trả accountId, displayName và avatarUrl; bỏ ID trùng, giữ thứ tự xuất hiện đầu tiên. "
            + "Bỏ qua ID không tồn tại, chưa có hồ sơ hoặc tài khoản đã xóa; không tìm thấy trả danh sách rỗng.")
    public ResponseEntity<ApiResponse<List<ProfileResponse>>> getProfilesBatch(HttpServletRequest request,
            @Parameter(description = "Account ID cần lấy hồ sơ; tối đa 100 phần tử trước khi bỏ trùng.")
            @RequestParam("ids") List<UUID> ids) {
        return ResponseEntity.ok(
                ApiResponse.<List<ProfileResponse>>builder()
                        .code(ApiResponse.SUCCESS_CODE)
                        .message("Lấy danh sách hồ sơ thành công.")
                        .result(profileService.getProfilesBatch(ids))
                        .path(request.getRequestURI())
                        .timestamp(Instant.now())
                        .build()
        );
    }
}
