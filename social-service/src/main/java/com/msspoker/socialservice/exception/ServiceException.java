package com.msspoker.socialservice.exception;

import org.springframework.http.HttpStatus;

public class ServiceException extends RuntimeException {
  public final String code;
  public final HttpStatus status;

  public ServiceException(String code, HttpStatus status, String message) {
    super(message);
    this.code = "SOCIAL_" + code;
    this.status = status;
  }

  public static ServiceException invalid(String message) {
    return new ServiceException("INVALID_REQUEST", HttpStatus.BAD_REQUEST, message);
  }

  public static ServiceException forbidden() {
    return new ServiceException(
        "FORBIDDEN", HttpStatus.FORBIDDEN, "Không có quyền thực hiện thao tác.");
  }

  public static ServiceException missing() {
    return new ServiceException("NOT_FOUND", HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên.");
  }

  public static ServiceException conflict(String message) {
    return new ServiceException("CONFLICT", HttpStatus.CONFLICT, message);
  }
}
