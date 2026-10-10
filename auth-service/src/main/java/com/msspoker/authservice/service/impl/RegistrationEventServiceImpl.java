package com.msspoker.authservice.service.impl;

import com.msspoker.authservice.entity.RegistrationEvent;
import com.msspoker.authservice.integration.messaging.RegistrationEventPublisher;
import com.msspoker.authservice.repository.RegistrationEventRepository;
import com.msspoker.authservice.service.RegistrationEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationEventServiceImpl implements RegistrationEventService {
    private final RegistrationEventRepository eventRepository;
    private final RegistrationEventPublisher eventPublisher;
    private final Clock clock;

    @Override
    @Transactional
    public void publishPendingEvents() {
        for (RegistrationEvent event : eventRepository.findPendingForUpdate()) {
            event.setDeliveryAttempts(event.getDeliveryAttempts() + 1);
            try {
                eventPublisher.publish(event);
                event.setPublishedAt(clock.instant());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception exception) {
                // The next dispatch retries with the original eventId, never a new business event.
                log.warn("Registration event {} remains pending ({})", event.getEventId(),
                        exception.getClass().getSimpleName());
            }
        }
    }
}
