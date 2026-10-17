package com.msspoker.matchmakingservice.exception;

public class GameCallUnknownException extends RuntimeException {
    public GameCallUnknownException(String message, Throwable cause) {
        super(message, cause);
    }
}
