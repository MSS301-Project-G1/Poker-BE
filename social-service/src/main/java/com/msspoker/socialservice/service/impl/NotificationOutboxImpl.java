package com.msspoker.socialservice.service.impl;

import com.msspoker.common.event.EventEnvelope;
import com.msspoker.socialservice.dto.event.Notification;
import com.msspoker.socialservice.entity.OutboxEntity;
import com.msspoker.socialservice.repository.OutboxRepository;
import com.msspoker.socialservice.service.NotificationOutbox;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
                new EventEnvelope<>(
                        UUID.randomUUID(), "notification.requested", clock.instant(), payload);
        var row = new OutboxEntity();
        row.setId(event.eventId());
        row.setBody(json.writeValueAsString(event));
        row.setOccurredAt(event.occurredAt());
        row.setNextAttemptAt(event.occurredAt());
        outbox.save(row);
    }
}
