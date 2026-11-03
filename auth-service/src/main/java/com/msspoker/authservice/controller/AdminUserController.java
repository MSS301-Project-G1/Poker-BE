package com.msspoker.authservice.controller;

import com.msspoker.authservice.dto.admin.request.BanUserRequest;
import com.msspoker.authservice.dto.admin.request.UnbanUserRequest;
import com.msspoker.authservice.dto.admin.response.AccountBanHistoryResponse;
import com.msspoker.authservice.dto.admin.response.AdminUserPageResponse;
import com.msspoker.authservice.dto.admin.response.AdminUserResponse;
import com.msspoker.authservice.service.AdminUserService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/users")
@Tag(name = "Admin - Người dùng", description = "Danh sách tài khoản, khóa/gỡ khóa và lịch sử; yêu cầu ADMIN đang hoạt động.")
public class AdminUserController {
    private final AdminUserService adminUserService;

    @GetMapping
    @Operation(summary = "Danh sách người dùng", description = "Tìm theo email, tên hiển thị hoặc mã người chơi; banned lọc trạng thái ban còn hiệu lực. Page bắt đầu từ 0, size 1–100.")
    public ResponseEntity<ApiResponse<AdminUserPageResponse>> getUsers(HttpServletRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "") String keyword, @RequestParam(required = false) Boolean banned,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                ApiResponse.<AdminUserPageResponse>builder()
                        .code(ApiResponse.SUCCESS_CODE)
                        .message("Lấy danh sách người dùng thành công.")
                        .result(adminUserService.getUsers(UUID.fromString(jwt.getSubject()), keyword, banned, page, size))
                        .path(request.getRequestURI())
                        .timestamp(Instant.now())
                        .build()
        );
    }

    @GetMapping("/{accountId}")
    @Operation(summary = "Chi tiết tài khoản dành cho admin")
    public ResponseEntity<ApiResponse<AdminUserResponse>> getUser(HttpServletRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId) {
        return ResponseEntity.ok(
                ApiResponse.<AdminUserResponse>builder()
                        .code(ApiResponse.SUCCESS_CODE)
                        .message("Lấy thông tin người dùng thành công.")
                        .result(adminUserService.getUser(UUID.fromString(jwt.getSubject()), accountId))
                        .path(request.getRequestURI())
                        .timestamp(Instant.now())
                        .build()
        );
    }

    @PostMapping("/{accountId}/ban")
    @SecurityRequirement(name = "csrfToken")
    @Operation(summary = "Khóa tài khoản", description = "reason bắt buộc, tối đa 500 ký tự; until là thời điểm UTC tương lai hoặc null để khóa đến khi gỡ. Thu hồi phiên hiện tại. Không khóa bản thân hoặc ADMIN khác.")
    public ResponseEntity<ApiResponse<AdminUserResponse>> banUser(HttpServletRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId,
            @Valid @RequestBody BanUserRequest banRequest) {
        return ResponseEntity.ok(
                ApiResponse.<AdminUserResponse>builder()
                        .code(ApiResponse.SUCCESS_CODE)
                        .message("Khóa tài khoản thành công.")
                        .result(adminUserService.banUser(UUID.fromString(jwt.getSubject()), accountId, banRequest))
                        .path(request.getRequestURI())
                        .timestamp(Instant.now())
                        .build()
        );
    }

    @PostMapping("/{accountId}/unban")
    @SecurityRequirement(name = "csrfToken")
    @Operation(summary = "Gỡ khóa tài khoản", description = "reason bắt buộc, tối đa 500 ký tự. User phải login lại; không khôi phục token đã thu hồi.")
    public ResponseEntity<ApiResponse<AdminUserResponse>> unbanUser(HttpServletRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId,
            @Valid @RequestBody UnbanUserRequest unbanRequest) {
        return ResponseEntity.ok(
                ApiResponse.<AdminUserResponse>builder()
                        .code(ApiResponse.SUCCESS_CODE)
                        .message("Gỡ khóa tài khoản thành công.")
                        .result(adminUserService.unbanUser(UUID.fromString(jwt.getSubject()), accountId, unbanRequest))
                        .path(request.getRequestURI())
                        .timestamp(Instant.now())
                        .build()
        );
    }

    @GetMapping("/{accountId}/bans")
    @Operation(summary = "Lịch sử khóa và gỡ khóa", description = "Mới nhất trước; không xóa lịch sử khi gỡ khóa hoặc hết hạn.")
    public ResponseEntity<ApiResponse<AccountBanHistoryResponse>> getBanHistory(HttpServletRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                ApiResponse.<AccountBanHistoryResponse>builder()
                        .code(ApiResponse.SUCCESS_CODE)
                        .message("Lấy lịch sử khóa tài khoản thành công.")
                        .result(adminUserService.getBanHistory(UUID.fromString(jwt.getSubject()), accountId, page, size))
                        .path(request.getRequestURI())
                        .timestamp(Instant.now())
                        .build()
        );
    }
}
