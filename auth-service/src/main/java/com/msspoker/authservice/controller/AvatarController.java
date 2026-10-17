package com.msspoker.authservice.controller;

import com.msspoker.authservice.dto.profile.request.AvatarPresignRequest;
import com.msspoker.authservice.dto.profile.request.ConfirmAvatarRequest;
import com.msspoker.authservice.dto.profile.request.SelectAvatarRequest;
import com.msspoker.authservice.dto.profile.response.AvatarOptionResponse;
import com.msspoker.authservice.dto.profile.response.AvatarPresignResponse;
import com.msspoker.authservice.dto.profile.response.ProfileResponse;
import com.msspoker.authservice.service.ProfileService;
import com.msspoker.authservice.shared.common.response.ApiResponse;
import com.msspoker.authservice.shared.infrastructure.storage.AvatarStorage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
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
@RequestMapping("/api/profiles")
@Tag(name = "Hồ sơ")
public class AvatarController {
    private final ProfileService profileService;
    private final AvatarStorage storage;

    @GetMapping("/avatars")
    @Operation(summary = "Danh sách avatar có sẵn", description = "Cần access cookie. Dùng avatarId để chọn; avatarUrl để hiển thị ảnh.")
    public ResponseEntity<ApiResponse<List<AvatarOptionResponse>>> presets(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.<List<AvatarOptionResponse>>builder().code(ApiResponse.SUCCESS_CODE)
                .message("Lấy danh sách avatar thành công.").result(profileService.getPresets())
                .path(request.getRequestURI()).timestamp(Instant.now()).build());
    }

    @PutMapping("/me/avatar")
    @SecurityRequirement(name = "csrfToken")
    @Operation(summary = "Chọn avatar có sẵn", description = "Cần access cookie và CSRF. Chỉ thay avatar của người trong JWT.")
    public ResponseEntity<ApiResponse<ProfileResponse>> select(HttpServletRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SelectAvatarRequest body) {
        return ResponseEntity.ok(ApiResponse.<ProfileResponse>builder().code(ApiResponse.SUCCESS_CODE)
                .message("Cập nhật avatar thành công.")
                .result(profileService.selectPreset(UUID.fromString(jwt.getSubject()), body))
                .path(request.getRequestURI()).timestamp(Instant.now()).build());
    }

    @GetMapping("/me/avatar/presign")
    @Operation(summary = "1. Xin presigned URL upload avatar lên S3", description = "Cần access cookie. Truyền fileName, contentType, fileSize qua query; không cần body hoặc CSRF. "
            + "BE trả key và URL PUT có hạn. FE PUT file trực tiếp lên uploadUrl với headers được trả về, không gửi cookie auth tới S3.")
    public ResponseEntity<ApiResponse<AvatarPresignResponse>> presign(HttpServletRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @RequestParam String fileName, @RequestParam String contentType, @RequestParam Long fileSize) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.<AvatarPresignResponse>builder().code(ApiResponse.SUCCESS_CODE)
                .message("Tạo URL upload avatar thành công.")
                .result(profileService.presign(UUID.fromString(jwt.getSubject()), new AvatarPresignRequest(fileName, contentType, fileSize)))
                .path(request.getRequestURI()).timestamp(Instant.now()).build());
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.APPLICATION_JSON_VALUE)
    @SecurityRequirement(name = "csrfToken")
    @Operation(summary = "2. Xác nhận avatar đã upload", description = "Sau khi FE PUT lên S3 thành công, gửi key từ bước presign. "
            + "BE kiểm tra quyền sở hữu key, ảnh thật PNG/JPEG, dung lượng và kích thước rồi cập nhật hồ sơ; cần access cookie và CSRF.")
    public ResponseEntity<ApiResponse<ProfileResponse>> confirm(HttpServletRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ConfirmAvatarRequest body) {
        return ResponseEntity.ok(ApiResponse.<ProfileResponse>builder().code(ApiResponse.SUCCESS_CODE)
                .message("Cập nhật avatar thành công.")
                .result(profileService.confirmUpload(UUID.fromString(jwt.getSubject()), body))
                .path(request.getRequestURI()).timestamp(Instant.now()).build());
    }

    @GetMapping(value = "/avatars/presets/{avatarId}", produces = "image/svg+xml")
    @Operation(summary = "Xem ảnh avatar có sẵn")
    public ResponseEntity<Resource> presetImage(@PathVariable String avatarId) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("image/svg+xml")).body(storage.presetImage(avatarId));
    }

}
