package com.msspoker.socialservice.exception;

import com.msspoker.socialservice.enums.SocialError;

import lombok.Getter;

import org.springframework.http.HttpStatus;

@Getter
public class SocialApiException extends RuntimeException {
    private final SocialError code;
    private final HttpStatus status;

    public SocialApiException(SocialError code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static SocialApiException invalid(String message) {
        return new SocialApiException(
                SocialError.SOCIAL_INVALID_REQUEST, HttpStatus.BAD_REQUEST, message);
    }

    public static SocialApiException forbidden() {
        return new SocialApiException(
                SocialError.SOCIAL_FORBIDDEN,
                HttpStatus.FORBIDDEN,
                "Không có quyền thực hiện thao tác.");
    }

    public static SocialApiException missing() {
        return new SocialApiException(
                SocialError.SOCIAL_NOT_FOUND, HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên.");
    }

    public static SocialApiException conflict(String message) {
        return new SocialApiException(SocialError.SOCIAL_CONFLICT, HttpStatus.CONFLICT, message);
    }
}
