package com.msspoker.authservice;

import com.msspoker.authservice.entity.Account;
import com.msspoker.authservice.entity.Profile;
import com.msspoker.authservice.enums.AccountRole;
import com.msspoker.authservice.enums.AccountStatus;
import com.msspoker.authservice.enums.GenderEnum;
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
import org.springframework.transaction.annotation.Propagation;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.LocalDate;
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
    @Autowired private DataSource dataSource;

    @Test
    void migrationAndPersistenceApplySafeDefaultsAndUuidV7() {
        Instant before = Instant.now();
        Account account = accounts.saveAndFlush(account("khanh@example.com"));
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
        assertThat(stored.isFirstLoginRewarded()).isFalse();
        assertThat(stored.getCreatedAt()).isBetween(before.minusMillis(1), Instant.now());
        assertThat(stored.getUpdatedAt()).isEqualTo(stored.getCreatedAt());
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }

    @Test
    void explicitSoftDeleteKeepsRowsButActiveQueriesExcludeThemAndEmailStaysReserved() {
        Account original = accounts.saveAndFlush(account("khanh@example.com"));
        profiles.saveAndFlush(profile(original, "Khanh"));
        original.setDeleted(true);
        accounts.saveAndFlush(original);
        entityManager.clear();

        assertThat(accounts.findById(original.getId())).isPresent();
        assertThat(accounts.findByEmail("khanh@example.com")).isPresent();
        assertThat(profiles.findByAccountId(original.getId())).isPresent();
        assertThat(accounts.findByIdAndDeletedFalse(original.getId())).isEmpty();
        assertThat(accounts.findByEmailAndDeletedFalse("khanh@example.com")).isEmpty();
        assertThat(profiles.findByAccountIdAndAccount_DeletedFalse(original.getId())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT is_deleted FROM accounts WHERE id = ?", Boolean.class, original.getId()))
                .isTrue();
        assertThatThrownBy(() -> accounts.saveAndFlush(account("khanh@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void profileAllowsDuplicateNamesButOnlyOnePerAccount() {
        Account first = accounts.saveAndFlush(account("first@example.com"));
        Account second = accounts.saveAndFlush(account("second@example.com"));
        profiles.saveAndFlush(profile(first, "Khanh"));
        profiles.saveAndFlush(profile(second, "Khanh"));

        assertThat(profiles.findByAccountId(first.getId())).isPresent();
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO profiles (account_id, display_name, created_at, updated_at)
                VALUES (?, 'Other name', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, first.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void profileCannotReferenceAnUnknownAccount() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO profiles (account_id, display_name, created_at, updated_at)
                VALUES (?, 'Khanh', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void profileUsesAccountIdentityAndKeepsAuditTimesWhenUpdated() {
        Account account = accounts.saveAndFlush(account("khanh@example.com"));
        Profile profile = profiles.saveAndFlush(profile(account, "Khanh"));
        entityManager.refresh(profile);
        Instant created = profile.getCreatedAt();
        assertThat(profile.getAccountId()).isEqualTo(account.getId());
        profile.setDisplayName("New name");
        profiles.flush();
        assertThat(profile.getCreatedAt()).isEqualTo(created);
        assertThat(profile.getUpdatedAt()).isAfterOrEqualTo(created);
        assertThat(profileMapper.toResponse(profile).getDisplayName()).isEqualTo("New name");
        assertThat(profileMapper.toResponse(profile).getAccountId()).isEqualTo(account.getId());

        entityManager.clear();
        Profile stored = profiles.findById(account.getId()).orElseThrow();
        assertThat(stored.getCreatedAt()).isEqualTo(created);
        assertThat(stored.getDisplayName()).isEqualTo("New name");
        assertThat(stored.getAccount().getId()).isEqualTo(stored.getAccountId());
        assertThat(accounts.findById(account.getId()).orElseThrow().getProfile().getAccountId())
                .isEqualTo(account.getId());
    }

    @Test
    void cascadePersistDerivesProfileIdFromNewAccountAndStoresUserFields() {
        Account account = account("khanh@example.com");
        account.setPhoneNumber("+84901234567");
        Profile profile = Profile.builder()
                .account(account)
                .displayName("Khanh")
                .fullName("Nguyen Khanh")
                .avatarUrl("https://example.com/avatar.png")
                .gender(GenderEnum.MALE)
                .birthDate(LocalDate.of(2004, 1, 2))
                .bio("Poker player")
                .countryCode("VN")
                .city("Ho Chi Minh City")
                .build();
        profiles.saveAndFlush(profile);
        UUID accountId = account.getId();
        assertThat(accountId.version()).isEqualTo(7);
        assertThat(profile.getAccountId()).isEqualTo(accountId);
        entityManager.clear();

        Profile stored = profiles.findById(accountId).orElseThrow();
        assertThat(stored.getFullName()).isEqualTo("Nguyen Khanh");
        assertThat(stored.getAvatarUrl()).isEqualTo("https://example.com/avatar.png");
        assertThat(stored.getGender()).isEqualTo(GenderEnum.MALE);
        assertThat(stored.getBirthDate()).isEqualTo(LocalDate.of(2004, 1, 2));
        assertThat(stored.getBio()).isEqualTo("Poker player");
        assertThat(stored.getCountryCode()).isEqualTo("VN");
        assertThat(stored.getCity()).isEqualTo("Ho Chi Minh City");
        assertThat(stored.getAccount().getPhoneNumber()).isEqualTo("+84901234567");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void migrationUpgradesExistingProfileToSharedPrimaryKeyWithoutLosingItsData() {
        Flyway.configure().dataSource(dataSource)
                .schemas("auth_upgrade_test").defaultSchema("auth_upgrade_test")
                .target("1").load().migrate();
        UUID accountId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO auth_upgrade_test.accounts (id, email, password_hash, created_at, updated_at)
                VALUES (?, 'upgrade@example.com', 'test-hash', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, accountId);
        jdbc.update("""
                INSERT INTO auth_upgrade_test.profiles (id, account_id, display_name, avatar_url, created_at, updated_at)
                VALUES (?, ?, 'Existing player', 'existing-avatar.png', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), accountId);

        Flyway.configure().dataSource(dataSource)
                .schemas("auth_upgrade_test").defaultSchema("auth_upgrade_test")
                .load().migrate();

        assertThat(jdbc.queryForObject("SELECT display_name FROM auth_upgrade_test.profiles WHERE account_id = ?",
                String.class, accountId)).isEqualTo("Existing player");
        assertThat(jdbc.queryForObject("SELECT avatar_url FROM auth_upgrade_test.profiles WHERE account_id = ?",
                String.class, accountId)).isEqualTo("existing-avatar.png");
        assertThat(jdbc.queryForObject("SELECT first_login_rewarded FROM auth_upgrade_test.accounts WHERE id = ?",
                Boolean.class, accountId)).isFalse();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'auth_upgrade_test' AND table_name = 'profiles'
                AND column_name IN ('id', 'is_deleted')
                """, Integer.class)).isZero();
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

    private Profile profile(Account account, String displayName) {
        return Profile.builder()
                .account(account)
                .displayName(displayName)
                .build();
    }
}
