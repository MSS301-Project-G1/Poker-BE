package com.msspoker.walletservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base exception cho toàn bộ wallet-service.
 * Chứa HttpStatus + errorCode để GlobalExceptionHandler xử lý tập trung.
 */
@Getter
public class AppException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    private AppException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public static AppException of(HttpStatus status, String errorCode, String message) {
        return new AppException(status, errorCode, message);
    }
}
