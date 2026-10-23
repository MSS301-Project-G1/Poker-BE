package com.msspoker.authservice.shared.infrastructure.security;

import com.msspoker.authservice.shared.common.exception.ApiException;
import com.msspoker.authservice.shared.common.exception.ErrorCode;
import com.msspoker.authservice.shared.common.exception.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

@RequiredArgsConstructor
public class SecurityErrorHandler {
    private final GlobalExceptionHandler exceptionHandler;
    private final JsonMapper mapper;

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code) throws IOException {
        var error = exceptionHandler.handleApiException(new ApiException(code), request);
        response.setStatus(error.getStatusCode().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), error.getBody());
    }
}
