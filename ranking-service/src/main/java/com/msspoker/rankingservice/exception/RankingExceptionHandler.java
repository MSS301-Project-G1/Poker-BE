package com.msspoker.rankingservice.exception;

import com.msspoker.rankingservice.constant.RankingHeaders;
import com.msspoker.rankingservice.dto.response.ErrorResponse;
import com.msspoker.rankingservice.enums.RankingError;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;

@Slf4j
@RestControllerAdvice
public class RankingExceptionHandler {

    @ExceptionHandler(RankingApiException.class)
    public ResponseEntity<ErrorResponse> business(RankingApiException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus()).body(error(ex.getCode(), ex.getMessage(), request));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class,
            HttpMessageNotReadableException.class, ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> validation(Exception ex, HttpServletRequest request) {
        return business(RankingExceptions.invalidRequest(), request);
    }

    /** The gateway always sets X-User-Id on authenticated routes; a missing header means no identity. */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> missingIdentity(MissingRequestHeaderException ex, HttpServletRequest request) {
        return business(RankingExceptions.unauthorized(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(NoResourceFoundException ex, HttpServletRequest request) {
        return business(RankingExceptions.notFound(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> methodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return unsupported(RankingExceptions.methodNotAllowed(), ex.getHeaders(), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> unsupportedMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return unsupported(RankingExceptions.unsupportedMediaType(), ex.getHeaders(), request);
    }

    /** A unique constraint caught a concurrent request (e.g. a double click); the data is still consistent. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> concurrentChange(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Request rejected by a database constraint: {}", ex.getMostSpecificCause().getMessage());
        return business(RankingExceptions.concurrentChange(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> invalid(IllegalArgumentException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(error(RankingError.RANK_INVALID_REQUEST, ex.getMessage(), request));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> conflict(IllegalStateException ex, HttpServletRequest request) {
        return ResponseEntity.status(409).body(error(RankingError.RANK_CONFLICT, ex.getMessage(), request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Ranking request failed", ex);
        return business(RankingExceptions.internalError(), request);
    }

    /** Keeps Spring's Allow (405) or Accept (415) header so the caller learns what is supported. */
    private ResponseEntity<ErrorResponse> unsupported(RankingApiException ex, HttpHeaders headers, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus()).headers(headers).body(error(ex.getCode(), ex.getMessage(), request));
    }

    private ErrorResponse error(RankingError code, String message, HttpServletRequest request) {
        return new ErrorResponse(code, message, Instant.now(), request.getHeader(RankingHeaders.REQUEST_ID));
    }
}
