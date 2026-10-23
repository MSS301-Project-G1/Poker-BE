package com.msspoker.competitionservice.service;

import com.msspoker.competitionservice.dto.event.MatchFinishedEvent;
import com.msspoker.competitionservice.dto.request.CreateRoomRequest;
import com.msspoker.competitionservice.dto.response.PageResponse;
import com.msspoker.competitionservice.dto.response.RoomResponse;
import com.msspoker.competitionservice.dto.response.RoomSummaryResponse;

import java.util.Optional;
import java.util.UUID;

public interface RoomService {
    RoomResponse create(UUID accountId, CreateRoomRequest request);

    PageResponse<RoomSummaryResponse> publicRooms(int page, int size);

    Optional<RoomResponse> mine(UUID accountId);

    RoomResponse get(String roomCode, UUID accountId);

    RoomResponse join(String roomCode, UUID accountId, String password);

    void leave(String roomCode, UUID accountId);

    RoomResponse ready(String roomCode, UUID accountId, boolean ready);

    RoomResponse kick(String roomCode, UUID ownerId, UUID targetId);

    RoomResponse transferOwner(String roomCode, UUID ownerId, UUID targetId);

    void invite(String roomCode, UUID accountId, UUID targetId);

    RoomResponse start(String roomCode, UUID accountId);

    void matchFinished(MatchFinishedEvent event);
}
