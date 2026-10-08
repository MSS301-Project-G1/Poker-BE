package com.msspoker.gameservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class GameApiException extends RuntimeException {
    private final GameError code;
    private final HttpStatus status;

    public GameApiException(GameError code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }
}
