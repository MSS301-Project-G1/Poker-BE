package com.msspoker.matchmakingservice.service;

import com.msspoker.matchmakingservice.dto.response.PartyInvitationResponse;
import com.msspoker.matchmakingservice.dto.response.PartyResponse;
import java.util.List;
import java.util.UUID;

public interface PartyInvitationService {
    PartyInvitationResponse invite(UUID actor, UUID target);
    List<PartyInvitationResponse> list(UUID actor, String direction);
    PartyResponse accept(UUID actor, UUID invitation);
    void decline(UUID actor, UUID invitation);
    void cancel(UUID actor, UUID invitation);
}
