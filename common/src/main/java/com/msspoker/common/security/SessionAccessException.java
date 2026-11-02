package com.msspoker.common.security;

public class SessionAccessException extends RuntimeException {
    private final String code;
    private final int status;

    public SessionAccessException(String code, int status) {
        super(code);
        this.code = code;
        this.status = status;
    }

    public String getCode() { return code; }
    public int getStatus() { return status; }
}
