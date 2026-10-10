package com.msspoker.authservice.integration.messaging;

import com.msspoker.authservice.service.RegistrationEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "auth.events.dispatch-enabled", havingValue = "true", matchIfMissing = true)
public class RegistrationEventScheduler {
    private final RegistrationEventService eventService;

    @Scheduled(fixedDelayString = "${auth.events.dispatch-delay}", initialDelayString = "${auth.events.dispatch-delay}")
    public void dispatch() {
        eventService.publishPendingEvents();
    }
}
