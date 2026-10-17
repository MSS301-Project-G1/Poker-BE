package com.msspoker.authservice.service;

import com.msspoker.authservice.dto.auth.request.LoginRequest;
import com.msspoker.authservice.dto.auth.response.CurrentUserResponse;
import com.msspoker.authservice.shared.infrastructure.security.SessionTokens;

import java.util.UUID;

public interface AuthService {
    SessionTokens login(LoginRequest request, String previousRefreshToken);
    SessionTokens refresh(String refreshToken);
    void logout(String refreshToken);
    CurrentUserResponse currentUser(UUID accountId);
}
