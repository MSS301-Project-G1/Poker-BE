package com.msspoker.apigateway.shared.exception;

import org.springframework.http.HttpStatus;

public enum GatewayErrorCode {
    GATEWAY_UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập lại."),
    GATEWAY_FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện yêu cầu này."),
    GATEWAY_ORIGIN_NOT_ALLOWED(HttpStatus.FORBIDDEN, "Nguồn yêu cầu không được phép."),
    GATEWAY_CSRF_INVALID(HttpStatus.FORBIDDEN, "CSRF token không hợp lệ."),
    GATEWAY_UPSTREAM_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ tạm thời không khả dụng."),
    GATEWAY_UPSTREAM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "Dịch vụ phản hồi quá thời gian cho phép."),
    GATEWAY_ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy đường dẫn yêu cầu."),
    GATEWAY_REQUEST_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể xử lý yêu cầu.");

    private final HttpStatus status;
    private final String message;

    GatewayErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
