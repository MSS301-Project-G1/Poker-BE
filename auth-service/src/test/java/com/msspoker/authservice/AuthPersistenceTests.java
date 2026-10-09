package com.msspoker.authservice;

import com.msspoker.authservice.entity.Account;
import com.msspoker.authservice.entity.Profile;
import com.msspoker.authservice.enums.AccountRole;
import com.msspoker.authservice.enums.AccountStatus;
import com.msspoker.authservice.mapper.ProfileMapper;
import com.msspoker.authservice.repository.AccountRepository;
import com.msspoker.authservice.repository.ProfileRepository;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
@Transactional
class AuthPersistenceTests {
    @Autowired private AccountRepository accounts;
    @Autowired private ProfileRepository profiles;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ProfileMapper profileMapper;
    @Autowired private Flyway flyway;

    @Test
    void migrationAndPersistenceApplySafeDefaultsAndUuidV7() {
        Instant before = Instant.now();
        Account account = accounts.saveAndFlush(account("  KHANH@Example.com  "));
        UUID id = account.getId();
        entityManager.clear();
        Account stored = accounts.findById(id).orElseThrow();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
        assertThat(Instant.ofEpochMilli(id.getMostSignificantBits() >>> 16))
                .isBetween(before.minusMillis(1), Instant.now());
        assertThat(stored.getEmail()).isEqualTo("khanh@example.com");
        assertThat(stored.getAccountStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(stored.getRole()).isEqualTo(AccountRole.USER);
        assertThat(stored.isEmailVerified()).isFalse();
        assertThat(stored.isDeleted()).isFalse();
        assertThat(stored.getCreatedAt()).isBetween(before.minusMillis(1), Instant.now());
        assertThat(stored.getUpdatedAt()).isEqualTo(stored.getCreatedAt());
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }

    @Test
    void normalizedEmailIsUniqueEvenAfterSoftDelete() {
        Account original = accounts.saveAndFlush(account("khanh@example.com"));
        accounts.delete(original);
        accounts.flush();
        entityManager.clear();

        assertThat(accounts.findById(original.getId())).isEmpty();
        assertThat(accounts.findByEmail("khanh@example.com")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT is_deleted FROM accounts WHERE id = ?", Boolean.class, original.getId()))
                .isTrue();
        assertThatThrownBy(() -> accounts.saveAndFlush(account(" KHANH@EXAMPLE.COM ")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void profileAllowsDuplicateNamesButOnlyOnePerAccount() {
        Account first = accounts.saveAndFlush(account("first@example.com"));
        Account second = accounts.saveAndFlush(account("second@example.com"));
        profiles.saveAndFlush(profile(first.getId(), "Khanh"));
        profiles.saveAndFlush(profile(second.getId(), "Khanh"));

        assertThat(profiles.findByAccountId(first.getId())).isPresent();
        assertThatThrownBy(() -> profiles.saveAndFlush(profile(first.getId(), "Other name")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void profileCannotReferenceAnUnknownAccount() {
        assertThatThrownBy(() -> profiles.saveAndFlush(profile(UUID.randomUUID(), "Khanh")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void updatesKeepCreatedTimeAndSoftDeleteHidesProfiles() {
        Account account = accounts.saveAndFlush(account("khanh@example.com"));
        Profile profile = profiles.saveAndFlush(profile(account.getId(), "Khanh"));
        Instant created = profile.getCreatedAt();
        profile.setDisplayName("New name");
        profiles.flush();
        assertThat(profile.getCreatedAt()).isEqualTo(created);
        assertThat(profile.getUpdatedAt()).isAfterOrEqualTo(created);
        assertThat(profileMapper.toResponse(profile).getDisplayName()).isEqualTo("New name");
        assertThat(profileMapper.toResponse(profile).getAccountId()).isEqualTo(account.getId());

        profiles.delete(profile);
        profiles.flush();
        entityManager.clear();
        assertThat(profiles.findByAccountId(account.getId())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT is_deleted FROM profiles WHERE id = ?", Boolean.class, profile.getId()))
                .isTrue();
    }

    @Test
    void passwordHashesAreSaltedAndCanBeVerified() {
        String raw = "test-password-123";
        String first = passwordEncoder.encode(raw);
        String second = passwordEncoder.encode(raw);
        assertThat(first).startsWith("{bcrypt}").isNotEqualTo(raw).isNotEqualTo(second);
        assertThat(passwordEncoder.matches(raw, first)).isTrue();
        assertThat(passwordEncoder.matches("incorrect-password", first)).isFalse();
    }

    private Account account(String email) {
        return Account.builder()
                .email(email)
                .passwordHash("{bcrypt}test-fixture-not-a-real-hash")
                .build();
    }

    private Profile profile(UUID accountId, String displayName) {
        return Profile.builder()
                .accountId(accountId)
                .displayName(displayName)
                .build();
    }
}
