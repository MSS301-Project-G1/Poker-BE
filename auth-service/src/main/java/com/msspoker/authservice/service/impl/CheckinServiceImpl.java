package com.msspoker.authservice.service.impl;

import com.msspoker.authservice.dto.checkin.response.CheckinCalendarResponse;
import com.msspoker.authservice.dto.checkin.response.CheckinResponse;
import com.msspoker.authservice.entity.Account;
import com.msspoker.authservice.entity.DailyCheckin;
import com.msspoker.authservice.enums.AccountStatus;
import com.msspoker.authservice.enums.CheckinRewardStatus;
import com.msspoker.authservice.integration.wallet.WalletClient;
import com.msspoker.authservice.mapper.CheckinMapper;
import com.msspoker.authservice.repository.AccountRepository;
import com.msspoker.authservice.repository.DailyCheckinRepository;
import com.msspoker.authservice.service.CheckinService;
import com.msspoker.authservice.shared.common.exception.ApiException;
import com.msspoker.authservice.shared.common.exception.ErrorCode;
import com.msspoker.authservice.shared.infrastructure.config.CheckinProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinServiceImpl implements CheckinService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final AccountRepository accountRepository;
    private final DailyCheckinRepository dailyCheckinRepository;
    private final CheckinMapper checkinMapper;
    private final CheckinProperties checkinProperties;
    private final WalletClient walletClient;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    @Override
    public CheckinResponse checkIn(UUID accountId) {
        UUID checkinId = new TransactionTemplate(transactionManager).execute(status -> {
            requireActive(accountRepository.findByIdForUpdate(accountId)
                    .orElseThrow(() -> new ApiException(ErrorCode.AUTH_UNAUTHENTICATED)));
            LocalDate date = LocalDate.now(clock.withZone(ZONE));
            if (dailyCheckinRepository.existsByAccountIdAndDate(accountId, date)) {
                throw new ApiException(ErrorCode.AUTH_CHECKIN_ALREADY_EXISTS);
            }
            Instant now = clock.instant();
            DailyCheckin checkin = DailyCheckin.builder()
                    .accountId(accountId)
                    .date(date)
                    .rewardCoins(checkinProperties.rewardCoins())
                    .rewardStatus(CheckinRewardStatus.PENDING)
                    .checkedInAt(now)
                    .nextAttemptAt(now)
                    .build();

            return dailyCheckinRepository.saveAndFlush(checkin).getId();
        });
        // The entitlement is durable before calling another service; failures cannot erase today's check-in.
        return deliverReward(checkinId);
    }

    @Override
    @Transactional(readOnly = true)
    public CheckinCalendarResponse getCalendar(UUID accountId, String month) {
        requireActive(accountRepository.findByIdAndDeletedFalse(accountId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_UNAUTHENTICATED)));
        if (month == null || !month.matches("[0-9]{4}-(0[1-9]|1[0-2])") || month.startsWith("0000")) {
            throw new ApiException(ErrorCode.AUTH_INVALID_REQUEST);
        }
        YearMonth selectedMonth = YearMonth.parse(month);
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        List<CheckinResponse> days = dailyCheckinRepository.findByAccountIdAndDateBetweenOrderByDateAsc(
                        accountId, selectedMonth.atDay(1), selectedMonth.atEndOfMonth())
                .stream()
                .map(checkinMapper::toResponse)
                .toList();

        return CheckinCalendarResponse.builder()
                .month(month)
                .timezone(ZONE.getId())
                .today(today)
                .checkedInToday(dailyCheckinRepository.existsByAccountIdAndDate(accountId, today))
                .days(days)
                .build();
    }

    @Override
    public void retryPendingRewards() {
        for (UUID checkinId : dailyCheckinRepository.findDueRewards(clock.instant(), PageRequest.of(0, 20))) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            deliverReward(checkinId);
        }
    }

    private CheckinResponse deliverReward(UUID checkinId) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            DailyCheckin checkin = dailyCheckinRepository.findByIdForUpdate(checkinId)
                    .orElseThrow(() -> new ApiException(ErrorCode.AUTH_NOT_FOUND));
            if (checkin.getRewardStatus() == CheckinRewardStatus.REWARDED
                    || checkin.getNextAttemptAt().isAfter(clock.instant())) {
                return checkinMapper.toResponse(checkin);
            }
            checkin.setDeliveryAttempts(checkin.getDeliveryAttempts() + 1);
            checkin.setNextAttemptAt(clock.instant().plus(checkinProperties.retryBackoff()));
            try {
                checkin.setWalletTransactionId(walletClient.creditCheckin(checkin));
                checkin.setRewardStatus(CheckinRewardStatus.REWARDED);
                checkin.setRewardedAt(clock.instant());
            } catch (ApiException exception) {
                if (exception.getErrorCode() != ErrorCode.AUTH_WALLET_UNAVAILABLE) {
                    throw exception;
                }
                // The same date/account key is reused after timeouts, crashes or a lost response.
                log.warn("Check-in {} is waiting for wallet credit", checkinId);
            }
            return checkinMapper.toResponse(checkin);
        });
    }

    private void requireActive(Account account) {
        if (!account.isEmailVerified() || account.getAccountStatus() == AccountStatus.PENDING_VERIFICATION) {
            throw new ApiException(ErrorCode.AUTH_EMAIL_VERIFICATION_PENDING);
        }
        if (account.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(ErrorCode.AUTH_ACCOUNT_UNAVAILABLE);
        }
    }
}
