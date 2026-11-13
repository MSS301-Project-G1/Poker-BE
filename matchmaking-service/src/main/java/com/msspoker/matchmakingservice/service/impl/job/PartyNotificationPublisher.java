package com.msspoker.matchmakingservice.service.impl.job;

import com.msspoker.matchmakingservice.repository.PartyNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

@Component
@RequiredArgsConstructor
@Slf4j
public class PartyNotificationPublisher {
    private final PartyNotificationRepository outbox;
    private final RabbitTemplate rabbit;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${matchmaking.party.notification-delay:1000}")
    @Transactional
    public void publish() {
        for (var row : outbox.pending()) {
            try {
                var properties = new MessageProperties();
                properties.setContentType("application/json");
                rabbit.send("poker.events", "notification.requested",
                        new Message(row.getBody().getBytes(StandardCharsets.UTF_8), properties));
                row.setPublishedAt(clock.instant());
            } catch (RuntimeException error) {
                log.warn("Party notification {} will retry", row.getId());
                break;
            }
        }
    }
}
