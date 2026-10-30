package com.msspoker.matchmakingservice.service.normal;

import com.msspoker.matchmakingservice.dto.response.QueueStatusResponse;
import com.msspoker.matchmakingservice.dto.response.NormalReconnectResponse;

import java.util.UUID;

public interface NormalMatchmakingService {
    QueueStatusResponse join(UUID accountId);
    void leave(UUID accountId);
    QueueStatusResponse status(UUID accountId);

    NormalReconnectResponse reconnect(UUID accountId);
}
