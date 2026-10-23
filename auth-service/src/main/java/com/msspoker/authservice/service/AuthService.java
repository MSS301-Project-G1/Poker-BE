package com.msspoker.authservice.service;

import com.msspoker.authservice.dto.auth.request.ChangePasswordRequest;
import com.msspoker.authservice.dto.auth.request.ForgotPasswordRequest;
import com.msspoker.authservice.dto.auth.request.LoginRequest;
import com.msspoker.authservice.dto.auth.request.RegisterRequest;
import com.msspoker.authservice.dto.auth.request.ResendRegistrationOtpRequest;
import com.msspoker.authservice.dto.auth.request.ResetPasswordRequest;
import com.msspoker.authservice.dto.auth.request.VerifyRegistrationOtpRequest;
import com.msspoker.authservice.dto.auth.response.CurrentUserResponse;
import com.msspoker.authservice.dto.auth.response.EmailVerificationResponse;
import com.msspoker.authservice.dto.auth.response.RegistrationResponse;
import com.msspoker.authservice.shared.infrastructure.security.SessionTokens;

import java.util.UUID;

public interface AuthService {
    RegistrationResponse register(RegisterRequest request);

    EmailVerificationResponse verifyOtp(VerifyRegistrationOtpRequest request);

    RegistrationResponse resendOtp(ResendRegistrationOtpRequest request);

    void forgotPassword(ForgotPasswordRequest request);
    void resetPassword(ResetPasswordRequest request);
    void changePassword(UUID accountId, ChangePasswordRequest request);

    SessionTokens login(LoginRequest request, String previousRefreshToken);
    SessionTokens refresh(String refreshToken);
    void logout(String refreshToken);
    CurrentUserResponse currentUser(UUID accountId);
}
