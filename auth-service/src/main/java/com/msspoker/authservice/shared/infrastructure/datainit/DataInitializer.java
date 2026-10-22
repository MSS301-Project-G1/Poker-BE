package com.msspoker.authservice.shared.infrastructure.datainit;

import com.msspoker.authservice.entity.Account;
import com.msspoker.authservice.entity.Profile;
import com.msspoker.authservice.entity.RegistrationEvent;
import com.msspoker.authservice.entity.RegistrationOtp;
import com.msspoker.authservice.enums.AccountRole;
import com.msspoker.authservice.enums.AccountStatus;
import com.msspoker.authservice.repository.AccountRepository;
import com.msspoker.authservice.repository.ProfileRepository;
import com.msspoker.authservice.repository.RegistrationEventRepository;
import com.msspoker.authservice.repository.RegistrationOtpRepository;
import com.msspoker.authservice.shared.infrastructure.config.DataInitProperties;
import com.msspoker.authservice.shared.infrastructure.config.DataInitProperties.SeedAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Creates the shared demo accounts at startup when their email is missing, so a fresh database
 * (new machine, `docker compose down -v`, first deploy) can log in at once. Existing accounts,
 * including changed passwords, are never touched.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "auth.data-init.enabled", havingValue = "true")
@EnableConfigurationProperties(DataInitProperties.class)
public class DataInitializer implements ApplicationRunner {
    private final DataInitProperties properties;
    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;
    private final RegistrationOtpRepository otpRepository;
    private final RegistrationEventRepository eventRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedVerified(properties.admin(), AccountRole.ADMIN);
        seedVerified(properties.user(), AccountRole.USER);
        seedVerified(properties.secondaryUser(), AccountRole.USER);
        seedUnverified(properties.unverifiedUser());
    }

    private void seedVerified(SeedAccount seed, AccountRole role) {
        Account account = create(seed, role, AccountStatus.ACTIVE, true);
        if (account == null) return;
        // The same outbox row as OTP verification, so ranking creates the player's Elo record.
        eventRepository.save(RegistrationEvent.builder().accountId(account.getId()).occurredAt(clock.instant()).build());
    }

    private void seedUnverified(SeedAccount seed) {
        Account account = create(seed, AccountRole.USER, AccountStatus.PENDING_VERIFICATION, false);
        if (account == null) return;
        // An already expired code: "resend OTP" mails a fresh one, as for a player whose code ran out.
        otpRepository.save(RegistrationOtp.builder().account(account)
                .codeHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .expiresAt(clock.instant()).lastSentAt(Instant.EPOCH).build());
    }

    /** Returns null when the email is already registered. */
    private Account create(SeedAccount seed, AccountRole role, AccountStatus status, boolean verified) {
        String email = seed.email().strip().toLowerCase(Locale.ROOT);
        if (accountRepository.findByEmail(email).isPresent()) {
            log.info("Demo account {} already exists; left unchanged", email);
            return null;
        }
        Account account = accountRepository.saveAndFlush(Account.builder().email(email)
                .passwordHash(passwordEncoder.encode(seed.password()))
                .role(role).accountStatus(status).emailVerified(verified).build());
        profileRepository.save(Profile.builder().account(account).displayName(seed.displayName().strip()).build());
        log.info("Created demo account {} ({}, {})", email, role, status);
        return account;
    }
}
