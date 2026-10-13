package com.msspoker.competitionservice.exception;

import com.msspoker.competitionservice.constant.CompetitionHeaders;
import com.msspoker.competitionservice.dto.response.ErrorResponse;
import com.msspoker.competitionservice.enums.CompetitionError;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;

@Slf4j
@RestControllerAdvice
public class CompetitionExceptionHandler {

    @ExceptionHandler(CompetitionApiException.class)
    public ResponseEntity<ErrorResponse> business(CompetitionApiException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus()).body(error(ex.getCode(), ex.getMessage(), request));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class,
            HttpMessageNotReadableException.class, ConstraintViolationException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> validation(Exception ex, HttpServletRequest request) {
        return business(CompetitionExceptions.invalidRequest(), request);
    }

    /** The gateway always sets X-User-Id on authenticated routes; a missing header means no identity. */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> missingIdentity(MissingRequestHeaderException ex, HttpServletRequest request) {
        return business(CompetitionExceptions.unauthorized(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(NoResourceFoundException ex, HttpServletRequest request) {
        return business(CompetitionExceptions.notFound(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> invalid(IllegalArgumentException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(error(CompetitionError.COMP_INVALID_REQUEST, ex.getMessage(), request));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> conflict(IllegalStateException ex, HttpServletRequest request) {
        return ResponseEntity.status(409).body(error(CompetitionError.COMP_CONFLICT, ex.getMessage(), request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Competition request failed", ex);
        return business(CompetitionExceptions.internalError(), request);
    }

    private ErrorResponse error(CompetitionError code, String message, HttpServletRequest request) {
        return new ErrorResponse(code, message, Instant.now(), request.getHeader(CompetitionHeaders.REQUEST_ID));
    }
}
