package com.msspoker.competitionservice.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Target account for owner transfer and invitations. */
public record RoomMemberRequest(@NotNull UUID accountId) {
}
