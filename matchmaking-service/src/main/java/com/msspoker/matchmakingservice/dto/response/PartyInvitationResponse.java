package com.msspoker.matchmakingservice.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PartyInvitationResponse(UUID id, UUID partyId, UUID inviterId, UUID recipientId,
                                      String status, Instant createdAt, Instant expiresAt) {}
