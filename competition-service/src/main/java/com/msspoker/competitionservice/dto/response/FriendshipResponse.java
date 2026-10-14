package com.msspoker.competitionservice.dto.response;

/** Response of social-service GET /internal/friends/{a}/with/{b} (SPEC §6.1). */
public record FriendshipResponse(boolean isFriend, boolean isBlocked) {
}
