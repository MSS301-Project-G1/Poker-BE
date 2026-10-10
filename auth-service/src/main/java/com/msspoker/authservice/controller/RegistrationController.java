package com.msspoker.authservice.controller;

import com.msspoker.authservice.dto.auth.request.RegisterRequest;
import com.msspoker.authservice.dto.auth.request.ResendRegistrationOtpRequest;
import com.msspoker.authservice.dto.auth.request.VerifyRegistrationOtpRequest;
import com.msspoker.authservice.dto.auth.response.EmailVerificationResponse;
import com.msspoker.authservice.dto.auth.response.RegistrationResponse;
import com.msspoker.authservice.service.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class RegistrationController {
    private final RegistrationService registrationService;

    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registrationService.register(request));
    }

    @PostMapping("/register/verify-otp")
    public ResponseEntity<EmailVerificationResponse> verifyOtp(@Valid @RequestBody VerifyRegistrationOtpRequest request) {
        return ResponseEntity.ok(registrationService.verifyOtp(request));
    }

    @PostMapping("/register/resend-otp")
    public ResponseEntity<RegistrationResponse> resendOtp(@Valid @RequestBody ResendRegistrationOtpRequest request) {
        return ResponseEntity.ok(registrationService.resendOtp(request));
    }
}
