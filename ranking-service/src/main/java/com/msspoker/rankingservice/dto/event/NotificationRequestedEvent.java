package com.msspoker.rankingservice.dto.event;

import com.msspoker.rankingservice.enums.NotificationType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Payload of notification.requested, delivered in realtime by social-service (SPEC §6.2). */
public record NotificationRequestedEvent(List<UUID> accountIds, NotificationType type, String title, String body,
                                         Map<String, Object> data) {
}
