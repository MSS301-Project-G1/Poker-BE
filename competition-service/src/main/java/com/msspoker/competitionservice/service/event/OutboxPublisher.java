package com.msspoker.competitionservice.service.event;

public interface OutboxPublisher {
    void publishPending();
}
