package com.msspoker.matchmakingservice.exception;

import com.msspoker.common.response.ErrorResponse;
import com.msspoker.matchmakingservice.enums.MatchmakingError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;

@RestControllerAdvice
public class MatchmakingExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(MatchmakingExceptionHandler.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @ExceptionHandler(MatchmakingApiException.class)
    public ResponseEntity<ErrorResponse> business(
            MatchmakingApiException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(exception.getStatus())
                .body(error(exception.getCode(), exception.getMessage(), request));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class,
            HttpMessageNotReadableException.class,
            ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> validation(Exception exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(error(
                MatchmakingError.MM_INVALID_REQUEST,
                "Dữ liệu không hợp lệ. Vui lòng kiểm tra thông tin gửi lên.",
                request
        ));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> missingIdentity(
            MissingRequestHeaderException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(401).body(error(
                MatchmakingError.MM_UNAUTHORIZED,
                "Phiên đăng nhập không hợp lệ hoặc đã hết hạn.",
                request
        ));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(NoResourceFoundException exception, HttpServletRequest request) {
        return ResponseEntity.status(404).body(error(
                MatchmakingError.MM_NOT_FOUND,
                "Không tìm thấy tài nguyên.",
                request
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> invalid(IllegalArgumentException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(error(
                MatchmakingError.MM_INVALID_REQUEST,
                exception.getMessage(),
                request
        ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> conflict(IllegalStateException exception, HttpServletRequest request) {
        return ResponseEntity.status(409).body(error(
                MatchmakingError.MM_CONFLICT,
                exception.getMessage(),
                request
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception exception, HttpServletRequest request) {
        log.error("Matchmaking request failed", exception);
        return ResponseEntity.internalServerError().body(error(
                MatchmakingError.MM_INTERNAL_ERROR,
                "Không thể xử lý yêu cầu lúc này. Vui lòng thử lại.",
                request
        ));
    }

    private ErrorResponse error(
            MatchmakingError code,
            String message,
            HttpServletRequest request
    ) {
        return ErrorResponse.builder()
                .code(code.name())
                .message(message)
                .timestamp(Instant.now())
                .requestId(request.getHeader(REQUEST_ID_HEADER))
                .build();
    }
}
