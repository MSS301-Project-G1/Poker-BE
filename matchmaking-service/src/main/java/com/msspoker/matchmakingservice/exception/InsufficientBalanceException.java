package com.msspoker.matchmakingservice.exception;

public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(String message, Throwable cause) {
        super(message, cause);
    }
}
