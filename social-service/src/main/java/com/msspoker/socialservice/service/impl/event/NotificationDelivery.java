package com.msspoker.socialservice.service.impl.event;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
@RequiredArgsConstructor
public class NotificationDelivery {
  private final SimpMessagingTemplate socket;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void deliver(NotificationConsumer.Delivered delivered) {
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
        .forEach(id -> socket.convertAndSendToUser(id.toString(), "/queue/notifications", dto));
  }
}
