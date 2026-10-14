package com.msspoker.authservice.controller;

import com.msspoker.authservice.dto.auth.request.LoginRequest;
import com.msspoker.authservice.dto.auth.response.CsrfResponse;
import com.msspoker.authservice.dto.auth.response.CurrentUserResponse;
import com.msspoker.authservice.dto.auth.response.SessionResponse;
import com.msspoker.authservice.dto.auth.response.WebSocketTokenResponse;
import com.msspoker.authservice.service.AuthService;
import com.msspoker.authservice.shared.common.response.ApiResponse;
import com.msspoker.authservice.shared.infrastructure.security.AuthCookieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Tag(name = "Phiên đăng nhập", description = "Cookie HttpOnly; lấy CSRF token trước khi login, refresh hoặc logout.")
public class AuthController {
    private final AuthService authService;
    private final AuthCookieService cookies;

    @GetMapping("/csrf")
    @Operation(summary = "1. Lấy CSRF token", description = "Lưu cookie XSRF-TOKEN và trả token. Swagger: copy result.token vào Authorize → csrfToken. FE gửi header X-XSRF-TOKEN và credentials: include.")
    public ResponseEntity<ApiResponse<CsrfResponse>> csrf(HttpServletRequest request,
            @Parameter(hidden = true) CsrfToken csrfToken) {
        return ResponseEntity.ok(ApiResponse.<CsrfResponse>builder().code(ApiResponse.SUCCESS_CODE)
                .message("Lấy CSRF token thành công.").result(new CsrfResponse(csrfToken.getHeaderName(), csrfToken.getToken()))
                .path(request.getRequestURI()).timestamp(Instant.now()).build());
    }

    @PostMapping("/login")
    @SecurityRequirement(name = "csrfToken")
    @Operation(summary = "2. Đăng nhập", description = "Email + mật khẩu; phải hoàn tất OTP đăng ký. Cấp accessToken/refreshToken bằng cookie HttpOnly. Nhiều thiết bị được login cùng lúc; login lại thay refresh token cũ trong cookie hiện tại. Chưa giới hạn login sai.")
    public ResponseEntity<ApiResponse<SessionResponse>> login(HttpServletRequest request, HttpServletResponse response,
            @Valid @RequestBody LoginRequest loginRequest) {
        var tokens = authService.login(loginRequest, cookies.read(request, AuthCookieService.REFRESH_COOKIE));
        cookies.write(response, tokens);
        return ResponseEntity.ok(ApiResponse.<SessionResponse>builder().code(ApiResponse.SUCCESS_CODE)
                .message("Đăng nhập thành công.").result(tokens.response())
                .path(request.getRequestURI()).timestamp(Instant.now()).build());
    }

    @PostMapping("/refresh")
    @SecurityRequirement(name = "csrfToken")
    @Operation(summary = "3. Làm mới phiên", description = "Đọc refreshToken từ cookie, đổi cả access và refresh. Token refresh cũ dùng lại trả 401; hạn phiên giữ nguyên từ login. FE chỉ gửi một refresh tại một thời điểm, rồi thử lại request cũ một lần. Không cần access token còn hạn.")
    public ResponseEntity<ApiResponse<SessionResponse>> refresh(HttpServletRequest request, HttpServletResponse response) {
        var tokens = authService.refresh(cookies.read(request, AuthCookieService.REFRESH_COOKIE));
        cookies.write(response, tokens);
        return ResponseEntity.ok(ApiResponse.<SessionResponse>builder().code(ApiResponse.SUCCESS_CODE)
                .message("Làm mới phiên đăng nhập thành công.").result(tokens.response())
                .path(request.getRequestURI()).timestamp(Instant.now()).build());
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "csrfToken")
    @Operation(summary = "4. Đăng xuất phiên hiện tại", description = "Thu hồi refresh token tương ứng ở DB và xóa cả hai cookie. Gọi lại vẫn thành công; không ảnh hưởng thiết bị khác. Access JWT đã cấp không bị chặn trước khi hết hạn.")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(cookies.read(request, AuthCookieService.REFRESH_COOKIE));
        cookies.clear(response);
        return ResponseEntity.ok(ApiResponse.<Void>builder().code(ApiResponse.SUCCESS_CODE)
                .message("Đăng xuất thành công.").path(request.getRequestURI()).timestamp(Instant.now()).build());
    }

    @GetMapping("/me")
    @Operation(summary = "5. Người dùng đang đăng nhập", description = "Đọc access cookie hợp lệ và trả tài khoản/hồ sơ cơ bản. Khi 401, FE gọi refresh rồi thử lại; chưa tự refresh ngầm trong endpoint.")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> me(HttpServletRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.<CurrentUserResponse>builder().code(ApiResponse.SUCCESS_CODE)
                .message("Lấy thông tin đăng nhập thành công.")
                .result(authService.currentUser(UUID.fromString(jwt.getSubject())))
                .path(request.getRequestURI()).timestamp(Instant.now()).build());
    }

    @GetMapping("/ws-token")
    @Operation(summary = "6. Lấy access token cho STOMP", description = "Sau login, gọi bằng cookie để lấy JWT hiện tại. Giữ trong bộ nhớ FE, gửi Authorization: Bearer <token> trong frame STOMP CONNECT; không lưu localStorage. Chưa tích hợp gateway/game trong phase này.")
    public ResponseEntity<ApiResponse<WebSocketTokenResponse>> webSocketToken(HttpServletRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        authService.currentUser(UUID.fromString(jwt.getSubject()));
        return ResponseEntity.ok(ApiResponse.<WebSocketTokenResponse>builder().code(ApiResponse.SUCCESS_CODE)
                .message("Lấy token kết nối WebSocket thành công.")
                .result(new WebSocketTokenResponse(jwt.getTokenValue(), jwt.getExpiresAt()))
                .path(request.getRequestURI()).timestamp(Instant.now()).build());
    }
}
