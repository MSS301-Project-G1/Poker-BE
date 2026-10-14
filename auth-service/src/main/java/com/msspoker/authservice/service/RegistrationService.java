package com.msspoker.authservice.service;

import com.msspoker.authservice.dto.auth.request.RegisterRequest;
import com.msspoker.authservice.dto.auth.request.ResendRegistrationOtpRequest;
import com.msspoker.authservice.dto.auth.request.VerifyRegistrationOtpRequest;
import com.msspoker.authservice.dto.auth.response.EmailVerificationResponse;
import com.msspoker.authservice.dto.auth.response.RegistrationResponse;

public interface RegistrationService {
    RegistrationResponse register(RegisterRequest request);

    EmailVerificationResponse verifyOtp(VerifyRegistrationOtpRequest request);

    RegistrationResponse resendOtp(ResendRegistrationOtpRequest request);
}
