package com.msspoker.authservice.controller;

import com.msspoker.authservice.dto.auth.request.RegisterRequest;
import com.msspoker.authservice.dto.auth.request.ResendRegistrationOtpRequest;
import com.msspoker.authservice.dto.auth.request.VerifyRegistrationOtpRequest;
import com.msspoker.authservice.dto.auth.response.EmailVerificationResponse;
import com.msspoker.authservice.dto.auth.response.RegistrationResponse;
import com.msspoker.authservice.service.RegistrationService;
import com.msspoker.common.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Tag(name = "Đăng ký và OTP", description = "Tạo tài khoản và xác thực email trước khi đăng nhập.")
public class RegistrationController {
    private final RegistrationService registrationService;

    @PostMapping("/register")
    @Operation(summary = "1. Đăng ký tài khoản", description = "Nhận email, password, confirmPassword và displayName. "
            + "Gửi OTP qua email; mở Mailpit local để lấy mã. Email chờ xác thực đăng ký lại không thay dữ liệu "
            + "hoặc tự gửi mã mới; tiếp tục xác thực hoặc dùng API gửi lại OTP.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tạo tài khoản chờ xác thực và SMTP đã nhận mail OTP.",
                    content = @Content(schema = @Schema(implementation = RegistrationResponse.class))),
            @ApiResponse(responseCode = "400", description = "AUTH_INVALID_REQUEST, AUTH_PASSWORD_MISMATCH hoặc AUTH_PASSWORD_INVALID.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "AUTH_EMAIL_VERIFICATION_PENDING, AUTH_EMAIL_IN_USE hoặc AUTH_CONFLICT.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "AUTH_MAIL_UNAVAILABLE: SMTP lỗi, rollback đăng ký.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registrationService.register(request));
    }

    @PostMapping("/register/verify-otp")
    @Operation(summary = "2. Xác thực OTP đăng ký", description = "Nhập email và mã OTP 6 chữ số từ mail. "
            + "Mã hợp lệ chỉ dùng một lần. Thành công chuyển ACTIVE và lưu account.registered để phát qua RabbitMQ; "
            + "FE về màn login, chưa cấp token/cookie. Sai mã vẫn tăng bộ đếm dù API trả lỗi.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Xác thực thành công, tài khoản ACTIVE.",
                    content = @Content(schema = @Schema(implementation = EmailVerificationResponse.class))),
            @ApiResponse(responseCode = "400", description = "AUTH_INVALID_REQUEST, AUTH_OTP_INVALID hoặc AUTH_OTP_EXPIRED.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "AUTH_EMAIL_ALREADY_VERIFIED hoặc AUTH_CONFLICT.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "AUTH_OTP_ATTEMPTS_EXCEEDED: hết số lần thử, cần gửi lại mã.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<EmailVerificationResponse> verifyOtp(@Valid @RequestBody VerifyRegistrationOtpRequest request) {
        return ResponseEntity.ok(registrationService.verifyOtp(request));
    }

    @PostMapping("/register/resend-otp")
    @Operation(summary = "3. Gửi lại OTP đăng ký", description = "Dùng khi chưa xác thực và đã hết thời gian chờ. "
            + "Mặc định cách lần gửi thành công trước ít nhất 60 giây. Mã mới thay mã cũ, reset bộ đếm sai; "
            + "SMTP lỗi thì giữ mã và bộ đếm cũ. Không dùng sau khi xác thực thành công.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã gửi mã mới, trả thời điểm hết hạn và cho phép gửi lại.",
                    content = @Content(schema = @Schema(implementation = RegistrationResponse.class))),
            @ApiResponse(responseCode = "400", description = "AUTH_INVALID_REQUEST hoặc AUTH_OTP_INVALID.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "AUTH_EMAIL_ALREADY_VERIFIED hoặc AUTH_CONFLICT.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "AUTH_OTP_RESEND_TOO_SOON: chưa đến resendAvailableAt.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "AUTH_MAIL_UNAVAILABLE: SMTP lỗi, giữ OTP trước đó.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RegistrationResponse> resendOtp(@Valid @RequestBody ResendRegistrationOtpRequest request) {
        return ResponseEntity.ok(registrationService.resendOtp(request));
    }
}
