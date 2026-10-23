package com.msspoker.gameservice.service.event;

public interface OutboxPublisher {
    void publishPending();
}
