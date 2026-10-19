package com.msspoker.competitionservice.service.client;

import com.msspoker.competitionservice.dto.request.CreateTableRequest;
import com.msspoker.competitionservice.dto.response.TableCreatedResponse;

public interface GameClient {
    /** game-service CreateTable; idempotent for the same (mode, sourceId) and payload. */
    TableCreatedResponse createTable(CreateTableRequest request);
}
