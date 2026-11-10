package com.msspoker.authservice;

import com.msspoker.authservice.entity.Account;
import com.msspoker.authservice.entity.RegistrationEvent;
import com.msspoker.authservice.enums.AccountRole;
import com.msspoker.authservice.enums.AccountStatus;
import com.msspoker.authservice.repository.AccountRepository;
import com.msspoker.authservice.repository.ProfileRepository;
import com.msspoker.authservice.repository.RegistrationEventRepository;
import com.msspoker.authservice.repository.RegistrationOtpRepository;
import com.msspoker.authservice.service.DemoAccountService;
import com.msspoker.authservice.shared.common.exception.ApiException;
import com.msspoker.authservice.shared.common.exception.ErrorCode;
import com.msspoker.authservice.shared.infrastructure.datainit.DataInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "auth.data-init.enabled=true",
        "auth.data-init.admin.email=Admin@Demo.local",
        "auth.data-init.admin.password=Admin@123456",
        "auth.data-init.admin.display-name=Quản trị viên Demo",
        "auth.data-init.user.email=user@demo.local",
        "auth.data-init.user.password=User@123456",
        "auth.data-init.user.display-name=Người dùng Demo",
        "auth.data-init.secondary-user.email=owner2@demo.local",
        "auth.data-init.secondary-user.password=User@123456",
        "auth.data-init.secondary-user.display-name=Người dùng Ownership",
        "auth.data-init.unverified-user.email=unverified@demo.local",
        "auth.data-init.unverified-user.password=User@123456",
        "auth.data-init.unverified-user.display-name=Người dùng Chưa xác minh"})
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class DataInitializerTests {
    @Autowired private DataInitializer initializer;
    @Autowired private AccountRepository accounts;
    @Autowired private ProfileRepository profiles;
    @Autowired private RegistrationOtpRepository otps;
    @Autowired private RegistrationEventRepository events;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DemoAccountService demoAccounts;

    @Test
    void startupCreatesLoginReadyDemoAccountsWithRegistrationEvents() {
        Account admin = accounts.findByEmail("admin@demo.local").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(AccountRole.ADMIN);
        assertThat(admin.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(admin.isEmailVerified()).isTrue();
        assertThat(passwordEncoder.matches("Admin@123456", admin.getPasswordHash())).isTrue();
        assertThat(profiles.findByAccountId(admin.getId()).orElseThrow().getDisplayName()).isEqualTo("Quản trị viên Demo");

        Account user = accounts.findByEmail("user@demo.local").orElseThrow();
        Account owner = accounts.findByEmail("owner2@demo.local").orElseThrow();
        assertThat(List.of(user.getRole(), owner.getRole())).containsOnly(AccountRole.USER);
        assertThat(passwordEncoder.matches("User@123456", owner.getPasswordHash())).isTrue();
        // Ranking creates Elo records from these events, exactly as for OTP-verified players.
        assertThat(events.findAll()).extracting(RegistrationEvent::getAccountId)
                .containsExactlyInAnyOrder(admin.getId(), user.getId(), owner.getId());

        Account unverified = accounts.findByEmail("unverified@demo.local").orElseThrow();
        assertThat(unverified.getAccountStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(unverified.isEmailVerified()).isFalse();
        // Already expired, so the resend cooldown has passed and verification needs a fresh code.
        assertThat(otps.findById(unverified.getId()).orElseThrow().getExpiresAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void demoAccountLookupResolvesOnlyConfiguredDemoEmails() {
        Account user = accounts.findByEmail("user@demo.local").orElseThrow();

        assertThat(demoAccounts.findDemoAccount(" User@Demo.local ").accountId()).isEqualTo(user.getId());
        // A real player's email must not resolve, even though the account exists.
        accounts.save(Account.builder().email("player@demo.local").passwordHash("x")
                .role(AccountRole.USER).accountStatus(AccountStatus.ACTIVE).emailVerified(true).build());
        assertThatThrownBy(() -> demoAccounts.findDemoAccount("player@demo.local"))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AUTH_NOT_FOUND));
    }

    @Test
    void laterStartupsKeepExistingAccountsUnchanged() throws Exception {
        Account user = accounts.findByEmail("user@demo.local").orElseThrow();
        user.setPasswordHash(passwordEncoder.encode("changed-by-player"));
        accounts.save(user);
        long accountCount = accounts.count();
        long eventCount = events.count();

        initializer.run(new DefaultApplicationArguments());

        assertThat(accounts.count()).isEqualTo(accountCount);
        assertThat(events.count()).isEqualTo(eventCount);
        UUID userId = user.getId();
        assertThat(passwordEncoder.matches("changed-by-player",
                accounts.findById(userId).orElseThrow().getPasswordHash())).isTrue();
    }
}
