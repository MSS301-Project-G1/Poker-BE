package com.msspoker.authservice.shared.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng."),
    AUTH_UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập hoặc làm mới phiên đăng nhập."),
    AUTH_REFRESH_INVALID(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập đã hết hạn hoặc không còn hợp lệ."),
    AUTH_ACCOUNT_UNAVAILABLE(HttpStatus.FORBIDDEN, "Tài khoản không thể đăng nhập."),
    AUTH_CSRF_INVALID(HttpStatus.FORBIDDEN, "CSRF token không hợp lệ. Vui lòng lấy lại token và thử lại."),
    AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập tài nguyên này."),
    AUTH_ORIGIN_NOT_ALLOWED(HttpStatus.FORBIDDEN, "Nguồn gửi yêu cầu không được phép."),
    AUTH_INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Dữ liệu yêu cầu không hợp lệ."),
    AUTH_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên."),
    AUTH_METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Phương thức không được hỗ trợ."),
    AUTH_CONFLICT(HttpStatus.CONFLICT, "Dữ liệu bị xung đột."),
    AUTH_UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Kiểu nội dung không được hỗ trợ."),
    AUTH_MAIL_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Chưa gửi được email, vui lòng thử lại sau."),
    AUTH_AVATAR_INVALID(HttpStatus.BAD_REQUEST, "Avatar không hợp lệ. Chọn ảnh có sẵn hoặc PNG/JPEG hợp lệ, mỗi cạnh tối đa 2048 px."),
    AUTH_AVATAR_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Ảnh vượt quá giới hạn dung lượng upload."),
    AUTH_AVATAR_UPLOAD_NOT_FOUND(HttpStatus.NOT_FOUND, "Chưa tìm thấy ảnh đã upload. Vui lòng hoàn tất PUT lên S3 trước."),
    AUTH_AVATAR_STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Chưa lưu được avatar, vui lòng thử lại sau."),
    AUTH_CURRENT_PASSWORD_INVALID(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không đúng."),
    AUTH_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "Xác nhận mật khẩu không khớp."),
    AUTH_PASSWORD_INVALID(HttpStatus.BAD_REQUEST, "Mật khẩu cần ít nhất 8 ký tự và tối đa 72 byte UTF-8."),
    AUTH_EMAIL_IN_USE(HttpStatus.CONFLICT, "Email đã được sử dụng."),
    AUTH_EMAIL_VERIFICATION_PENDING(HttpStatus.CONFLICT, "Email đang chờ xác thực. Vui lòng nhập OTP hoặc gửi lại mã."),
    AUTH_EMAIL_ALREADY_VERIFIED(HttpStatus.CONFLICT, "Email đã được xác thực. Vui lòng đăng nhập."),
    AUTH_OTP_INVALID(HttpStatus.BAD_REQUEST, "Mã OTP hoặc email không hợp lệ."),
    AUTH_OTP_EXPIRED(HttpStatus.BAD_REQUEST, "Mã OTP đã hết hạn. Vui lòng gửi lại mã."),
    AUTH_OTP_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Đã hết số lần thử OTP. Vui lòng gửi lại mã."),
    AUTH_OTP_RESEND_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "Chưa đến thời gian gửi lại OTP."),
    AUTH_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Có lỗi xảy ra, vui lòng thử lại sau.");

    private final HttpStatus status;
    private final String message;
}
