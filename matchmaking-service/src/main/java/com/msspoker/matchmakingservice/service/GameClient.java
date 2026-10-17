package com.msspoker.matchmakingservice.service;

import com.msspoker.matchmakingservice.dto.request.CreateTableRequest;
import com.msspoker.matchmakingservice.dto.response.GameMatchSettingsResponse;
import com.msspoker.matchmakingservice.dto.response.TableCreatedResponse;
import com.msspoker.matchmakingservice.enums.MatchMode;

import java.util.Optional;
import java.util.UUID;

public interface GameClient {
    GameMatchSettingsResponse matchSettings(MatchMode mode);

    TableCreatedResponse createTable(CreateTableRequest request);

    Optional<TableCreatedResponse> activeTable(UUID accountId);
}
