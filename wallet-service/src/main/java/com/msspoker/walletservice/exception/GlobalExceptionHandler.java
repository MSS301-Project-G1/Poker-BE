package com.msspoker.walletservice.exception;

import com.msspoker.walletservice.dto.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    /**
     * Handle all AppException instances (Rule #19).
     * HttpStatus + errorCode đều nằm trong AppException — không cần handler riêng cho từng loại.
     */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex, HttpServletRequest request) {
        log.warn("Business error [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return buildResponse(ex.getStatus(), ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .orElse("Dữ liệu yêu cầu không hợp lệ");
        log.warn("Validation error: {}", message);
        return buildResponse(HttpStatus.BAD_REQUEST, "WALLET_VALIDATION_ERROR", message, request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        String message = "Thiếu header bắt buộc: " + ex.getHeaderName();
        log.warn("Missing header: {}", ex.getHeaderName());
        return buildResponse(HttpStatus.BAD_REQUEST, "WALLET_MISSING_HEADER", message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Malformed JSON or missing body: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "WALLET_BAD_REQUEST", "Dữ liệu JSON không hợp lệ hoặc thiếu request body", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = "Tham số '" + ex.getName() + "' không đúng định dạng";
        log.warn("Type mismatch: parameter={}, value={}", ex.getName(), ex.getValue());
        return buildResponse(HttpStatus.BAD_REQUEST, "WALLET_BAD_REQUEST", message, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "WALLET_BAD_REQUEST", ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on URI: {} - Error: ", request.getRequestURI(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "WALLET_INTERNAL_ERROR", "Đã xảy ra lỗi nội bộ, vui lòng thử lại sau.", request);
    }

    private ResponseEntity<ApiResponse<Void>> buildResponse(HttpStatus status, String code, String message, HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        ApiResponse<Void> apiResponse = ApiResponse.error(code, message, requestId);
        return ResponseEntity.status(status).body(apiResponse);
    }
}
