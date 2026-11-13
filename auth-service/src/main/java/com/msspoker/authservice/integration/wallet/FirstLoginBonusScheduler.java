package com.msspoker.authservice.integration.wallet;

import com.msspoker.authservice.service.FirstLoginBonusService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "auth.first-login-bonus.delivery-enabled", havingValue = "true", matchIfMissing = true)
public class FirstLoginBonusScheduler {
    private final FirstLoginBonusService firstLoginBonusService;

    @Scheduled(fixedDelayString = "${auth.first-login-bonus.delivery-delay}",
            initialDelayString = "${auth.first-login-bonus.delivery-delay}")
    public void deliver() {
        firstLoginBonusService.deliverPendingBonuses();
    }
}
