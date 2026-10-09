package com.msspoker.gameservice.service;

import com.msspoker.gameservice.dto.request.ActionRequest;
import com.msspoker.gameservice.dto.response.GameSnapshotResponse;

import java.util.UUID;

public interface GameplayService {
    GameSnapshotResponse snapshot(UUID tableId, UUID accountId);

    void sync(UUID tableId, UUID accountId);

    void action(UUID tableId, UUID accountId, ActionRequest request);

    void tick();
}
