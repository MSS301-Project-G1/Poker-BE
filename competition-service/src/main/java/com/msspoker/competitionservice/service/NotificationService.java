package com.msspoker.competitionservice.service;

import com.msspoker.competitionservice.enums.NotificationType;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface NotificationService {
    /** Stores notification.requested in the outbox within the caller's transaction. */
    void notify(NotificationType type, Collection<UUID> accountIds, String title, String body, Map<String, Object> data);
}
