package com.msspoker.socialservice.dto.event;

import com.msspoker.common.event.EventEnvelope;

public record NotificationReceivedEvent(EventEnvelope<Notification> event) {}
