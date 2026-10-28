package com.msspoker.matchmakingservice.service.client.stub;

import com.msspoker.matchmakingservice.dto.request.CreateTableRequest;
import com.msspoker.matchmakingservice.dto.response.GameMatchSettingsResponse;
import com.msspoker.matchmakingservice.dto.response.TableCreatedResponse;
import com.msspoker.matchmakingservice.enums.MatchMode;
import com.msspoker.matchmakingservice.service.client.GameClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@ConditionalOnProperty(name = "matchmaking.game.stub-enabled", havingValue = "true")
public class StubGameClient implements GameClient {
    private final ConcurrentMap<UUID, TableCreatedResponse> bySource = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, TableCreatedResponse> byAccount = new ConcurrentHashMap<>();

    @Override
    public GameMatchSettingsResponse matchSettings(MatchMode mode) {
        return new GameMatchSettingsResponse(mode, 2, 6);
    }

    @Override
    public TableCreatedResponse createTable(CreateTableRequest request) {
        TableCreatedResponse response = bySource.computeIfAbsent(
                request.sourceId(),
                ignored -> new TableCreatedResponse(UUID.randomUUID(), UUID.randomUUID())
        );
        request.playerIds().forEach(accountId -> byAccount.put(accountId, response));
        return response;
    }

    @Override
    public Optional<TableCreatedResponse> activeTable(UUID accountId) {
        return Optional.ofNullable(byAccount.get(accountId));
    }
}
