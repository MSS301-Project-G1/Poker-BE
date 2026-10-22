package com.msspoker.matchmakingservice.service;

import com.msspoker.matchmakingservice.dto.response.PartyResponse;

import java.util.UUID;

public interface PartyService {
    java.util.Optional<PartyResponse> current(UUID accountId);
    PartyResponse create(UUID accountId);
    void invite(UUID accountId, UUID inviteeId);
    PartyResponse join(UUID accountId, String partyCode);
    void kick(UUID accountId, UUID targetAccountId);
    void leave(UUID accountId);
}
