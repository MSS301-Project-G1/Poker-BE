package com.msspoker.matchmakingservice.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PartyInviteRequest(@NotNull UUID inviteeId) {
}
