package com.msspoker.socialservice.service.impl;

import com.msspoker.socialservice.service.*;
import com.msspoker.common.event.EventEnvelope;
import com.msspoker.socialservice.dto.event.Notification;
import com.msspoker.socialservice.entity.OutboxEvent;
import com.msspoker.socialservice.repository.OutboxRepository;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class NotificationOutboxImpl implements NotificationOutbox {
  private final OutboxRepository outbox;
  private final JsonMapper json;
  private final Clock clock;

  @Transactional(propagation = Propagation.MANDATORY)
  @Override
  public void add(UUID recipient, String type, UUID actor, UUID request) {
    var payload =
        new Notification(
            List.of(recipient),
            type,
            type.equals("FRIEND_REQUEST") ? "Lời mời kết bạn" : "Đã chấp nhận kết bạn",
            "",
            json.valueToTree(Map.of("accountId", actor, "requestId", request)));
    var event =
        new EventEnvelope<>(UUID.randomUUID(), "notification.requested", clock.instant(), payload);
    var row = new OutboxEvent();
    row.setId(event.eventId());
    row.setBody(json.writeValueAsString(event));
    row.setOccurredAt(event.occurredAt());
    row.setNextAttemptAt(event.occurredAt());
    outbox.save(row);
  }
}
