package com.msspoker.authservice.dto.admin.response;

import com.msspoker.authservice.enums.AccountRole;
import com.msspoker.authservice.enums.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AdminUserResponse {
    private UUID accountId;
    private String email;
    private String displayName;
    private String playerCode;
    private AccountRole role;
    private AccountStatus accountStatus;
    private boolean emailVerified;
    private boolean banned;
    private Instant bannedUntil;
    private String banReason;
    private Instant bannedAt;
    private UUID bannedBy;
    private Instant createdAt;
}
