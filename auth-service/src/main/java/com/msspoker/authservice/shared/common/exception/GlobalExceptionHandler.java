package com.msspoker.authservice.shared.common.exception;

import com.msspoker.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException exception, HttpServletRequest request) {
        ErrorCode errorCode = exception.getErrorCode();
        ErrorResponse response = buildErrorResponse(errorCode, request);
        if (exception.getCustomMessage() != null) {
            response.setMessage(exception.getCustomMessage());
        }
        return ResponseEntity.status(errorCode.getStatus()).body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException exception, HttpServletRequest request) {
        return handleApiException(new ApiException(ErrorCode.AUTH_INVALID_REQUEST), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException exception, HttpServletRequest request) {
        // Constraint names, SQL and submitted values must not reach the response.
        return handleApiException(new ApiException(ErrorCode.AUTH_CONFLICT), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUncategorizedException(Exception exception, HttpServletRequest request) {
        log.error("Unhandled auth exception", exception);
        return handleApiException(new ApiException(ErrorCode.AUTH_INTERNAL_ERROR), request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object ignoredBody,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ErrorCode errorCode = switch (status.value()) {
            case 404 -> ErrorCode.AUTH_NOT_FOUND;
            case 405 -> ErrorCode.AUTH_METHOD_NOT_ALLOWED;
            case 415 -> ErrorCode.AUTH_UNSUPPORTED_MEDIA_TYPE;
            default -> status.is5xxServerError() ? ErrorCode.AUTH_INTERNAL_ERROR : ErrorCode.AUTH_INVALID_REQUEST;
        };
        HttpServletRequest servletRequest = ((ServletWebRequest) request).getRequest();
        return super.handleExceptionInternal(exception, buildErrorResponse(errorCode, servletRequest), headers, status, request);
    }

    private ErrorResponse buildErrorResponse(ErrorCode errorCode, HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank() || requestId.length() > 128) {
            requestId = UUID.randomUUID().toString();
        }
        return ErrorResponse.builder()
                .code(errorCode.name())
                .message(errorCode.getMessage())
                .timestamp(Instant.now())
                .requestId(requestId)
                .build();
    }
}
