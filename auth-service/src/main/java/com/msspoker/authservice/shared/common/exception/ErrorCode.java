package com.msspoker.authservice.shared.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    AUTH_INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Dữ liệu yêu cầu không hợp lệ."),
    AUTH_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên."),
    AUTH_METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Phương thức không được hỗ trợ."),
    AUTH_CONFLICT(HttpStatus.CONFLICT, "Dữ liệu bị xung đột."),
    AUTH_UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Kiểu nội dung không được hỗ trợ."),
    AUTH_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Có lỗi xảy ra, vui lòng thử lại sau.");

    private final HttpStatus status;
    private final String message;
}
