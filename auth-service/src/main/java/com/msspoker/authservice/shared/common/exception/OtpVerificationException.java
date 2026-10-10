package com.msspoker.authservice.shared.common.exception;

// These failures still commit the attempt counter; other business failures roll back normally.
public class OtpVerificationException extends ApiException {
    public OtpVerificationException(ErrorCode errorCode) {
        super(errorCode);
    }
}
