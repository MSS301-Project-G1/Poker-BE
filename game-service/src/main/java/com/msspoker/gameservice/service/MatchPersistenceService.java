package com.msspoker.gameservice.service;

import com.msspoker.gameservice.dto.request.CreateTableRequest;
import com.msspoker.gameservice.dto.response.MatchHistoryResponse;
import com.msspoker.gameservice.dto.response.PageResponse;
import com.msspoker.gameservice.entity.MatchEntity;
import com.msspoker.gameservice.model.game.ManagedTable;
import com.msspoker.gameservice.model.game.PokerTable;

import java.util.*;

public interface MatchPersistenceService {
    MatchEntity started(CreateTableRequest request, PokerTable table);

    void finished(ManagedTable table);

    Optional<MatchEntity> existing(CreateTableRequest request);

    PageResponse<MatchHistoryResponse> history(UUID accountId, int page, int size);

    MatchHistoryResponse result(UUID tableId, UUID accountId);

    void cancelInterruptedMatches();
}
