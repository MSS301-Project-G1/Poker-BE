package com.msspoker.authservice.dto.auth.response;

import com.msspoker.authservice.enums.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmailVerificationResponse {
    private UUID accountId;
    private String email;
    private AccountStatus accountStatus;
}
