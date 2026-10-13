package com.msspoker.competitionservice.service.impl;

import com.msspoker.common.event.EventEnvelope;
import com.msspoker.competitionservice.constant.CompetitionEventConstants;
import com.msspoker.competitionservice.dto.event.NotificationRequestedEvent;
import com.msspoker.competitionservice.entity.OutboxEntity;
import com.msspoker.competitionservice.enums.NotificationType;
import com.msspoker.competitionservice.repository.OutboxRepository;
import com.msspoker.competitionservice.service.NotificationService;
import com.msspoker.competitionservice.util.CompetitionIds;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {
    private final OutboxRepository outbox;
    private final JsonMapper json;

    @Transactional
    @Override
    public void notify(NotificationType type, Collection<UUID> accountIds, String title, String body,
                       Map<String, Object> data) {
        List<UUID> recipients = List.copyOf(new LinkedHashSet<>(accountIds));
        if (recipients.isEmpty()) return;
        OutboxEntity row = new OutboxEntity();
        row.setId(CompetitionIds.next());
        row.setEventType(CompetitionEventConstants.NOTIFICATION_REQUESTED);
        row.setOccurredAt(Instant.now());
        row.setBody(json.writeValueAsString(new EventEnvelope<>(row.getId(), row.getEventType(), row.getOccurredAt(),
                new NotificationRequestedEvent(recipients, type, title, body, data))));
        outbox.save(row);
    }
}
