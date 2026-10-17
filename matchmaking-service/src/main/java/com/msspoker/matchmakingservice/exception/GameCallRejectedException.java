package com.msspoker.matchmakingservice.exception;

public class GameCallRejectedException extends RuntimeException {
    public GameCallRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
