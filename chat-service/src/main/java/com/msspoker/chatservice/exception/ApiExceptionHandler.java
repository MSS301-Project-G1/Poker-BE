package com.msspoker.chatservice.exception;

import com.msspoker.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(ServiceException.class)
  ResponseEntity<ErrorResponse> business(ServiceException e, HttpServletRequest r) {
    return response(e.status.value(), e.code, e.getMessage(), r);
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    MethodArgumentTypeMismatchException.class,
    HttpMessageNotReadableException.class,
    IllegalArgumentException.class
  })
  ResponseEntity<ErrorResponse> invalid(Exception e, HttpServletRequest r) {
    return response(400, "CHAT_INVALID_REQUEST", "Dữ liệu không hợp lệ.", r);
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  ResponseEntity<ErrorResponse> identity(Exception e, HttpServletRequest r) {
    return response(401, "CHAT_UNAUTHORIZED", "Thiếu danh tính từ Gateway.", r);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorResponse> unexpected(Exception e, HttpServletRequest r) {
    org.slf4j.LoggerFactory.getLogger(getClass())
        .error("Request failed requestId={}", r.getHeader("X-Request-Id"), e);
    return response(500, "CHAT_INTERNAL_ERROR", "Không thể xử lý yêu cầu.", r);
  }

  private ResponseEntity<ErrorResponse> response(
      int status, String code, String message, HttpServletRequest r) {
    return ResponseEntity.status(status)
        .body(new ErrorResponse(code, message, Instant.now(), r.getHeader("X-Request-Id")));
  }
}
