package com.msspoker.gameservice.api;

import com.msspoker.gameservice.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.time.Instant;

@Slf4j
@RestControllerAdvice
public class GameExceptionHandler {
    public record ErrorResponse(GameError code, String message, Instant timestamp, String requestId) {
    }

    @ExceptionHandler(GameApiException.class)
    public ResponseEntity<ErrorResponse> business(GameApiException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus()).body(error(ex.getCode(), ex.getMessage(), request));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class,
            HttpMessageNotReadableException.class, ConstraintViolationException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> validation(Exception ex, HttpServletRequest request) {
        return business(GameExceptions.invalidRequest(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> action(IllegalArgumentException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(error(GameError.GAME_INVALID_ACTION, ex.getMessage(), request));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> conflict(IllegalStateException ex, HttpServletRequest request) {
        return ResponseEntity.status(409).body(error(GameError.GAME_CONFLICT, ex.getMessage(), request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Game request failed", ex);
        return business(GameExceptions.internalError(), request);
    }

    private ErrorResponse error(GameError code, String message, HttpServletRequest request) {
        return new ErrorResponse(code, message, Instant.now(), request.getHeader("X-Request-Id"));
    }
}
