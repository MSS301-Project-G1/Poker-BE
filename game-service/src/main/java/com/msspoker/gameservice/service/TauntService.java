package com.msspoker.gameservice.service;

import com.msspoker.gameservice.dto.request.TauntRequest;

import java.util.UUID;

public interface TauntService {
    void taunt(UUID tableId, UUID accountId, TauntRequest request);
}
