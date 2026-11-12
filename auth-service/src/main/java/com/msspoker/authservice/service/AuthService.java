package com.msspoker.authservice.service;

import com.msspoker.authservice.dto.auth.request.ChangePasswordRequest;
import com.msspoker.authservice.dto.auth.request.ForgotPasswordRequest;
import com.msspoker.authservice.dto.auth.request.LoginRequest;
import com.msspoker.authservice.dto.auth.request.RegisterRequest;
import com.msspoker.authservice.dto.auth.request.ResendRegistrationOtpRequest;
import com.msspoker.authservice.dto.auth.request.ResetPasswordRequest;
import com.msspoker.authservice.dto.auth.request.VerifyRegistrationOtpRequest;
import com.msspoker.authservice.dto.auth.response.CurrentUserResponse;
import com.msspoker.authservice.dto.auth.response.AccountRestrictionsResponse;
import com.msspoker.authservice.dto.auth.response.SessionStateResponse;
import com.msspoker.authservice.dto.auth.response.EmailVerificationResponse;
import com.msspoker.authservice.dto.auth.response.RegistrationResponse;
import com.msspoker.authservice.shared.infrastructure.security.SessionTokens;

import java.util.List;
import java.util.UUID;

public interface AuthService {
    AccountRestrictionsResponse restrictions(UUID accountId);

    List<UUID> adminIds();
    RegistrationResponse register(RegisterRequest request);

    EmailVerificationResponse verifyOtp(VerifyRegistrationOtpRequest request);

    RegistrationResponse resendOtp(ResendRegistrationOtpRequest request);

    void forgotPassword(ForgotPasswordRequest request);
    void resetPassword(ResetPasswordRequest request);
    void changePassword(UUID accountId, ChangePasswordRequest request);

    SessionTokens login(LoginRequest request, UUID tabId);
    SessionTokens refresh(String refreshToken, UUID tabId);
    SessionTokens claimTab(String refreshToken, UUID tabId);
    void logout(String refreshToken, UUID tabId);
    CurrentUserResponse currentUser(UUID accountId);
    SessionStateResponse sessionState(UUID accountId, UUID sessionId, UUID tabId);
}
