package com.msspoker.rankingservice.service.event;

public interface OutboxPublisher {
    void publishPending();
}
