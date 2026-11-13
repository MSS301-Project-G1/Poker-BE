package com.msspoker.authservice.dto.admin.response;

import com.msspoker.authservice.enums.AccountRole;
import com.msspoker.authservice.enums.AccountStatus;
import com.msspoker.authservice.enums.GenderEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Everything an admin may read about one account; credentials and tokens are never included. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AdminUserDetailResponse {
    private UUID accountId;
    private String email;
    private String playerCode;
    private String displayName;
    private String fullName;
    private String phoneNumber;
    private GenderEnum gender;
    private LocalDate birthDate;
    private String bio;
    private String countryCode;
    private String city;
    private String avatarUrl;
    /** True when the player chose a preset or uploaded an image instead of the default avatar. */
    private boolean customAvatar;
    private AccountRole role;
    private AccountStatus accountStatus;
    private boolean emailVerified;
    private boolean firstLoginRewarded;
    private boolean banned;
    private Instant bannedUntil;
    private String banReason;
    private Instant bannedAt;
    private UUID bannedBy;
    private Instant chatBannedUntil;
    private Instant rankBannedUntil;
    private Instant createdAt;
    private Instant updatedAt;
}
