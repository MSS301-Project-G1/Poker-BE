package com.msspoker.competitionservice.dto.response;

import com.msspoker.competitionservice.enums.RoomStatus;
import com.msspoker.competitionservice.enums.RoomVisibility;

import java.util.List;
import java.util.UUID;

/** Room detail for members; members are listed in join order, which is the seat order. */
public record RoomResponse(UUID id, String roomCode, UUID ownerId, RoomVisibility visibility, boolean hasPassword,
                           int maxPlayers, long smallBlind, long bigBlind, RoomStatus status, UUID tableId,
                           List<RoomMemberResponse> members) {
}
