package com.msspoker.matchmakingservice.exception;

import com.msspoker.matchmakingservice.enums.MatchmakingError;
import org.springframework.http.HttpStatus;

public class MatchmakingApiException extends RuntimeException {
    private final MatchmakingError code;
    private final HttpStatus status;

    public MatchmakingApiException(MatchmakingError code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public MatchmakingError getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
