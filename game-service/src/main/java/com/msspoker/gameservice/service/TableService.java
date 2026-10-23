package com.msspoker.gameservice.service;

import com.msspoker.gameservice.dto.request.CreateTableRequest;
import com.msspoker.gameservice.dto.response.TableCreatedResponse;

import java.util.Optional;
import java.util.UUID;

public interface TableService {
    TableCreatedResponse create(CreateTableRequest request);

    Optional<TableCreatedResponse> active(UUID accountId);
}
