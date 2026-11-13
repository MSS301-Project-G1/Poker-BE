package com.msspoker.authservice.service.impl;

import com.msspoker.authservice.entity.Account;
import com.msspoker.authservice.entity.FirstLoginBonus;
import com.msspoker.authservice.enums.AccountRole;
import com.msspoker.authservice.enums.FirstLoginBonusStatus;
import com.msspoker.authservice.integration.wallet.WalletClient;
import com.msspoker.authservice.repository.AccountRepository;
import com.msspoker.authservice.repository.FirstLoginBonusRepository;
import com.msspoker.authservice.service.FirstLoginBonusService;
import com.msspoker.authservice.shared.common.exception.ApiException;
import com.msspoker.authservice.shared.common.exception.ErrorCode;
import com.msspoker.authservice.shared.infrastructure.config.FirstLoginBonusProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FirstLoginBonusServiceImpl implements FirstLoginBonusService {
    private final AccountRepository accountRepository;
    private final FirstLoginBonusRepository firstLoginBonusRepository;
    private final FirstLoginBonusProperties firstLoginBonusProperties;
    private final WalletClient walletClient;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void queue(Account account) {
        // Admins do not play, so only player accounts get the bonus.
        if (account.getRole() != AccountRole.USER) {
            return;
        }
        Instant now = clock.instant();
        firstLoginBonusRepository.save(FirstLoginBonus.builder()
                .accountId(account.getId())
                .rewardCoins(firstLoginBonusProperties.rewardCoins())
                .rewardStatus(FirstLoginBonusStatus.PENDING)
                .createdAt(now)
                .nextAttemptAt(now)
                .build());
    }

    @Override
    public void deliverPendingBonuses() {
        for (UUID bonusId : firstLoginBonusRepository.findDueBonuses(clock.instant(), PageRequest.of(0, 20))) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            deliver(bonusId);
        }
    }

    private void deliver(UUID bonusId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            FirstLoginBonus bonus = firstLoginBonusRepository.findByIdForUpdate(bonusId)
                    .orElseThrow(() -> new ApiException(ErrorCode.AUTH_NOT_FOUND));
            if (bonus.getRewardStatus() == FirstLoginBonusStatus.REWARDED
                    || bonus.getNextAttemptAt().isAfter(clock.instant())) {
                return;
            }
            bonus.setDeliveryAttempts(bonus.getDeliveryAttempts() + 1);
            bonus.setNextAttemptAt(clock.instant().plus(firstLoginBonusProperties.retryBackoff()));
            try {
                bonus.setWalletTransactionId(walletClient.creditFirstLoginBonus(bonus));
                bonus.setRewardStatus(FirstLoginBonusStatus.REWARDED);
                bonus.setRewardedAt(clock.instant());
                accountRepository.findById(bonus.getAccountId())
                        .ifPresent(account -> account.setFirstLoginRewarded(true));
            } catch (ApiException exception) {
                if (exception.getErrorCode() != ErrorCode.AUTH_WALLET_UNAVAILABLE) {
                    throw exception;
                }
                // The key is per account, so a retry after a lost response never credits twice.
                log.warn("First-login bonus {} is waiting for wallet credit", bonusId);
            }
        });
    }
}
