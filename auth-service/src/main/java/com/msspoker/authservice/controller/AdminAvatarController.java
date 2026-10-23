package com.msspoker.authservice.controller;

import com.msspoker.authservice.dto.profile.request.AvatarPresignRequest;
import com.msspoker.authservice.dto.profile.request.CreateAvatarPresetRequest;
import com.msspoker.authservice.dto.profile.request.UpdateAvatarPresetRequest;
import com.msspoker.authservice.dto.profile.response.AdminAvatarPresetResponse;
import com.msspoker.authservice.dto.profile.response.AvatarPresignResponse;
import com.msspoker.authservice.service.ProfileService;
import com.msspoker.authservice.shared.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
@RequestMapping("/api/admin/avatars")
@Tag(name = "Admin - Avatar", description = "Danh mục avatar trên S3; yêu cầu tài khoản ADMIN đang hoạt động.")
public class AdminAvatarController {
    private final ProfileService profileService;

    @GetMapping
    @Operation(summary = "Danh sách avatar dành cho admin", description = "Bao gồm cả avatar đang tắt.")
    public ResponseEntity<ApiResponse<List<AdminAvatarPresetResponse>>> getPresets(HttpServletRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(
                ApiResponse.<List<AdminAvatarPresetResponse>>builder()
                        .code(ApiResponse.SUCCESS_CODE)
                        .message("Lấy danh sách avatar thành công.")
                        .result(profileService.getAdminPresets(UUID.fromString(jwt.getSubject())))
                        .path(request.getRequestURI())
                        .timestamp(Instant.now())
                        .build()
        );
    }

    @GetMapping("/presign")
    @Operation(summary = "1. Xin URL upload avatar có sẵn", description = "Nhận fileName, contentType, fileSize qua query; "
            + "chỉ PNG/JPEG. FE PUT file trực tiếp lên uploadUrl với headers trả về, không gửi cookie auth tới S3. Không cần CSRF.")
    public ResponseEntity<ApiResponse<AvatarPresignResponse>> presign(HttpServletRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @RequestParam String fileName, @RequestParam String contentType, @RequestParam Long fileSize) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(
                        ApiResponse.<AvatarPresignResponse>builder()
                                .code(ApiResponse.SUCCESS_CODE)
                                .message("Tạo URL upload avatar thành công.")
                                .result(profileService.presignPreset(UUID.fromString(jwt.getSubject()),
                                        new AvatarPresignRequest(fileName, contentType, fileSize)))
                                .path(request.getRequestURI())
                                .timestamp(Instant.now())
                                .build()
                );
    }

    @PostMapping
    @SecurityRequirement(name = "csrfToken")
    @Operation(summary = "2. Xác nhận upload và tạo avatar", description = "Gửi name và key từ bước presign sau khi PUT lên S3. "
            + "BE kiểm tra ảnh thật, lưu S3 key và bật avatar. Gửi lại cùng key trả cùng avatar, không tạo bản sao.")
    public ResponseEntity<ApiResponse<AdminAvatarPresetResponse>> createPreset(HttpServletRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateAvatarPresetRequest createRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<AdminAvatarPresetResponse>builder()
                        .code(ApiResponse.SUCCESS_CODE)
                        .message("Tạo avatar thành công.")
                        .result(profileService.createPreset(UUID.fromString(jwt.getSubject()), createRequest))
                        .path(request.getRequestURI())
                        .timestamp(Instant.now())
                        .build()
        );
    }

    @PutMapping("/{avatarId}")
    @SecurityRequirement(name = "csrfToken")
    @Operation(summary = "Đổi tên và bật/tắt avatar", description = "Gửi đủ name và active. Tắt avatar ngăn chọn mới; "
            + "không xóa ảnh hoặc thay avatar của user đã chọn. Muốn thay ảnh, tạo avatar mới.")
    public ResponseEntity<ApiResponse<AdminAvatarPresetResponse>> updatePreset(HttpServletRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID avatarId, @Valid @RequestBody UpdateAvatarPresetRequest updateRequest) {
        return ResponseEntity.ok(
                ApiResponse.<AdminAvatarPresetResponse>builder()
                        .code(ApiResponse.SUCCESS_CODE)
                        .message("Cập nhật avatar thành công.")
                        .result(profileService.updatePreset(UUID.fromString(jwt.getSubject()), avatarId, updateRequest))
                        .path(request.getRequestURI())
                        .timestamp(Instant.now())
                        .build()
        );
    }
}
