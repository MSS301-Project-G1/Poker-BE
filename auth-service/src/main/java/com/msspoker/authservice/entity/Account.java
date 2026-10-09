package com.msspoker.authservice.entity;

import com.msspoker.authservice.enums.AccountRole;
import com.msspoker.authservice.enums.AccountStatus;
import com.msspoker.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.Setter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class Account extends BaseEntity {
    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 32)
    private AccountStatus accountStatus = AccountStatus.PENDING_VERIFICATION;

    @Column(name = "is_email_verified", nullable = false)
    private boolean emailVerified;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccountRole role = AccountRole.USER;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @Column(name = "first_login_rewarded", nullable = false)
    private boolean firstLoginRewarded;

    @OneToOne(mappedBy = "account", fetch = FetchType.LAZY)
    private Profile profile;
}
