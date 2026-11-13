package com.msspoker.socialservice.service.impl.event;

import com.msspoker.socialservice.dto.event.NotificationReceivedEvent;

import lombok.RequiredArgsConstructor;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class NotificationDelivery {
    private final SimpMessagingTemplate socket;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliver(NotificationReceivedEvent delivered) {
        var e = delivered.event();
        var p = e.payload();
        var dto =
                Map.of(
                        "eventId",
                        e.eventId(),
                        "type",
                        p.type(),
                        "title",
                        p.title(),
                        "body",
                        p.body(),
                        "data",
                        p.data(),
                        "occurredAt",
                        e.occurredAt());
        p.accountIds().stream()
                .distinct()
                .forEach(
                        id ->
                                socket.convertAndSendToUser(
                                        id.toString(), "/queue/notifications", dto));
    }
}
