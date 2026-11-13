package com.msspoker.authservice;

import com.msspoker.authservice.dto.auth.request.VerifyRegistrationOtpRequest;
import com.msspoker.authservice.entity.Account;
import com.msspoker.authservice.entity.FirstLoginBonus;
import com.msspoker.authservice.entity.RegistrationOtp;
import com.msspoker.authservice.enums.AccountStatus;
import com.msspoker.authservice.enums.FirstLoginBonusStatus;
import com.msspoker.authservice.repository.AccountRepository;
import com.msspoker.authservice.repository.FirstLoginBonusRepository;
import com.msspoker.authservice.repository.RegistrationOtpRepository;
import com.msspoker.authservice.service.AuthService;
import com.msspoker.authservice.service.FirstLoginBonusService;
import com.msspoker.authservice.shared.common.exception.ApiException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresTestConfiguration.class, CheckinTests.TimeConfiguration.class})
class FirstLoginBonusTests {
    private static final Instant NOW = Instant.parse("2026-11-13T03:00:00Z");
    private static final String OTP = "012345";
    private static final CheckinTests.WalletFixture WALLET = new CheckinTests.WalletFixture();
    @Autowired private AuthService authService;
    @Autowired private FirstLoginBonusService service;
    @Autowired private AccountRepository accounts;
    @Autowired private RegistrationOtpRepository otps;
    @Autowired private FirstLoginBonusRepository bonuses;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private CheckinTests.MutableClock clock;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("auth.wallet.base-url", WALLET::url);
        registry.add("wallet.internal-key", () -> "wallet-test-key");
        registry.add("auth.wallet.request-timeout", () -> "500ms");
    }

    @BeforeEach
    void setup() {
        bonuses.deleteAllInBatch();
        WALLET.mode.set("SUCCESS");
        WALLET.ledger.clear();
        WALLET.requests.set(0);
        WALLET.last.set(null);
        WALLET.onCredit.set(null);
        clock.now.set(NOW);
    }

    @AfterAll
    static void closeFixture() { WALLET.close(); }

    @Test
    void verifyingANewAccountQueuesFiveHundredCoinsThatTheJobCreditsOnce() {
        Account player = pendingPlayer();
        authService.verifyOtp(new VerifyRegistrationOtpRequest(player.getEmail(), OTP));

        FirstLoginBonus queued = bonusOf(player);
        assertThat(queued.getRewardStatus()).isEqualTo(FirstLoginBonusStatus.PENDING);
        assertThat(queued.getRewardCoins()).isEqualTo(500);
        // The OTP request never waits on wallet; the job credits the bonus.
        assertThat(WALLET.requests.get()).isZero();

        service.deliverPendingBonuses();
        var command = WALLET.last.get();
        assertThat(command.get("accountId").asString()).isEqualTo(player.getId().toString());
        assertThat(command.get("amount").asLong()).isEqualTo(500);
        assertThat(command.get("reason").asString()).isEqualTo("FIRST_LOGIN_BONUS");
        assertThat(command.get("refId").asString()).isEqualTo(player.getId().toString());
        assertThat(command.get("idempotencyKey").asString()).isEqualTo("first-login:" + player.getId());
        assertThat(command.get("currencyType").asString()).isEqualTo("COIN");
        FirstLoginBonus rewarded = bonusOf(player);
        assertThat(rewarded.getRewardStatus()).isEqualTo(FirstLoginBonusStatus.REWARDED);
        assertThat(rewarded.getRewardedAt()).isEqualTo(NOW);
        assertThat(rewarded.getWalletTransactionId()).isNotNull();
        assertThat(accounts.findById(player.getId()).orElseThrow().isFirstLoginRewarded()).isTrue();

        clock.now.set(NOW.plusSeconds(3600));
        service.deliverPendingBonuses();
        assertThat(WALLET.requests.get()).isEqualTo(1);
    }

    @Test
    void walletOutagesAndLostResponsesStayPendingAndRetryWithTheSameKey() {
        Account player = pendingPlayer();
        authService.verifyOtp(new VerifyRegistrationOtpRequest(player.getEmail(), OTP));

        WALLET.mode.set("ERROR");
        service.deliverPendingBonuses();
        FirstLoginBonus waiting = bonusOf(player);
        assertThat(waiting.getRewardStatus()).isEqualTo(FirstLoginBonusStatus.PENDING);
        assertThat(waiting.getDeliveryAttempts()).isEqualTo(1);
        assertThat(waiting.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(accounts.findById(player.getId()).orElseThrow().isFirstLoginRewarded()).isFalse();

        // Wallet books the credit but the answer never arrives.
        clock.now.set(NOW.plusSeconds(30));
        WALLET.mode.set("LOST_RESPONSE");
        service.deliverPendingBonuses();
        assertThat(bonusOf(player).getRewardStatus()).isEqualTo(FirstLoginBonusStatus.PENDING);

        WALLET.mode.set("SUCCESS");
        service.deliverPendingBonuses();
        assertThat(WALLET.requests.get()).as("waits for the retry backoff").isEqualTo(2);
        clock.now.set(NOW.plusSeconds(60));
        service.deliverPendingBonuses();
        assertThat(bonusOf(player).getRewardStatus()).isEqualTo(FirstLoginBonusStatus.REWARDED);
        assertThat(bonusOf(player).getDeliveryAttempts()).isEqualTo(3);
        assertThat(WALLET.ledger).containsOnlyKeys("first-login:" + player.getId());
    }

    @Test
    void existingAccountsAndFailedVerificationsGetNoBonus() {
        // Already active before the bonus existed, so nothing was ever queued for it.
        accounts.saveAndFlush(Account.builder().email(UUID.randomUUID() + "@example.com").passwordHash("unused")
                .emailVerified(true).accountStatus(AccountStatus.ACTIVE).build());
        Account player = pendingPlayer();
        assertThatThrownBy(() -> authService.verifyOtp(new VerifyRegistrationOtpRequest(player.getEmail(), "999999")))
                .isInstanceOf(ApiException.class);

        service.deliverPendingBonuses();
        assertThat(bonuses.count()).isZero();
        assertThat(WALLET.requests.get()).isZero();
    }

    private Account pendingPlayer() {
        Account account = accounts.saveAndFlush(Account.builder().email(UUID.randomUUID() + "@example.com")
                .passwordHash("unused").accountStatus(AccountStatus.PENDING_VERIFICATION).build());
        otps.saveAndFlush(RegistrationOtp.builder().account(account).codeHash(passwordEncoder.encode(OTP))
                .expiresAt(NOW.plusSeconds(300)).lastSentAt(NOW).build());
        return account;
    }

    private FirstLoginBonus bonusOf(Account account) {
        return bonuses.findAll().stream().filter(bonus -> bonus.getAccountId().equals(account.getId()))
                .findFirst().orElseThrow();
    }
}
