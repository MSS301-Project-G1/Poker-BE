package com.msspoker.gameservice.exception;

import com.msspoker.common.response.ErrorResponse;
import com.msspoker.gameservice.constant.GameHeaders;
import com.msspoker.gameservice.enums.GameError;
import com.msspoker.gameservice.mapper.ErrorMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GameExceptionHandler {
    private final ErrorMapper errors;

    @ExceptionHandler(GameApiException.class)
    public ResponseEntity<ErrorResponse> business(GameApiException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus()).body(errors.error(ex, request.getHeader(GameHeaders.REQUEST_ID)));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class,
            HttpMessageNotReadableException.class, ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
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
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error(GameError.GAME_CONFLICT, ex.getMessage(), request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Game request failed", ex);
        return business(GameExceptions.internalError(), request);
    }

    private ErrorResponse error(GameError code, String message, HttpServletRequest request) {
        return errors.error(code, message, request.getHeader(GameHeaders.REQUEST_ID));
    }
}
