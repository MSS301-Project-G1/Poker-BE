package com.msspoker.authservice.dto.auth.response;

public record CsrfResponse(String headerName, String token) {
}
