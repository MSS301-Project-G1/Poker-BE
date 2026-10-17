package com.msspoker.rankingservice.service.impl;

import com.msspoker.common.event.EventEnvelope;
import com.msspoker.rankingservice.constant.RankingEventConstants;
import com.msspoker.rankingservice.dto.event.NotificationRequestedEvent;
import com.msspoker.rankingservice.entity.OutboxEntity;
import com.msspoker.rankingservice.enums.NotificationType;
import com.msspoker.rankingservice.repository.OutboxRepository;
import com.msspoker.rankingservice.service.NotificationService;
import com.msspoker.rankingservice.util.RankingIds;

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
        row.setId(RankingIds.next());
        row.setEventType(RankingEventConstants.NOTIFICATION_REQUESTED);
        row.setOccurredAt(Instant.now());
        row.setBody(json.writeValueAsString(new EventEnvelope<>(row.getId(), row.getEventType(), row.getOccurredAt(),
                new NotificationRequestedEvent(recipients, type, title, body, data))));
        outbox.save(row);
    }
}
