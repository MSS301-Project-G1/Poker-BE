package com.msspoker.authservice.integration.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "auth.events.dispatch-enabled", havingValue = "true", matchIfMissing = true)
public class RegistrationEventScheduler {
    private final RegistrationEventDispatcher dispatcher;

    @Scheduled(fixedDelayString = "${auth.events.dispatch-delay}", initialDelayString = "${auth.events.dispatch-delay}")
    public void dispatch() {
        dispatcher.publishPendingEvents();
    }
}
