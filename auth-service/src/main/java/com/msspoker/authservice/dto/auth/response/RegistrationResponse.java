package com.msspoker.authservice.dto.auth.response;

import com.msspoker.authservice.enums.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegistrationResponse {
    private UUID accountId;
    private String email;
    private AccountStatus accountStatus;
    private Instant otpExpiresAt;
    private Instant resendAvailableAt;
}
