package com.msspoker.authservice.integration.wallet;

import com.msspoker.authservice.service.CheckinService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "auth.checkin.retry-enabled", havingValue = "true", matchIfMissing = true)
public class CheckinRewardScheduler {
    private final CheckinService checkinService;

    @Scheduled(fixedDelayString = "${auth.checkin.retry-delay}", initialDelayString = "${auth.checkin.retry-delay}")
    public void retry() {
        checkinService.retryPendingRewards();
    }
}
