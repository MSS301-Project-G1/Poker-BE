package com.msspoker.competitionservice.controller;

import com.msspoker.competitionservice.constant.CompetitionConstants;
import com.msspoker.competitionservice.constant.CompetitionHeaders;
import com.msspoker.competitionservice.dto.request.CreateRoomRequest;
import com.msspoker.competitionservice.dto.request.JoinRoomRequest;
import com.msspoker.competitionservice.dto.request.ReadyRequest;
import com.msspoker.competitionservice.dto.request.RoomMemberRequest;
import com.msspoker.competitionservice.dto.response.PageResponse;
import com.msspoker.competitionservice.dto.response.RoomResponse;
import com.msspoker.competitionservice.dto.response.RoomSummaryResponse;
import com.msspoker.competitionservice.service.RoomService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rooms")
public class RoomController {
    private final RoomService rooms;

    @PostMapping
    public RoomResponse create(@RequestHeader(CompetitionHeaders.USER_ID) UUID accountId,
            @Valid @RequestBody CreateRoomRequest request) {
        return rooms.create(accountId, request);
    }

    @GetMapping
    public PageResponse<RoomSummaryResponse> publicRooms(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(CompetitionConstants.MAX_PAGE_SIZE) int size) {
        return rooms.publicRooms(page, size);
    }

    @GetMapping("/me")
    public Optional<RoomResponse> mine(@RequestHeader(CompetitionHeaders.USER_ID) UUID accountId) {
        return rooms.mine(accountId);
    }

    @GetMapping("/{roomCode}")
    public RoomResponse get(@PathVariable String roomCode, @RequestHeader(CompetitionHeaders.USER_ID) UUID accountId) {
        return rooms.get(roomCode, accountId);
    }

    @PostMapping("/{roomCode}/join")
    public RoomResponse join(@PathVariable String roomCode, @RequestHeader(CompetitionHeaders.USER_ID) UUID accountId,
            @RequestBody(required = false) JoinRoomRequest request) {
        return rooms.join(roomCode, accountId, request == null ? null : request.password());
    }

    @PostMapping("/{roomCode}/leave")
    public void leave(@PathVariable String roomCode, @RequestHeader(CompetitionHeaders.USER_ID) UUID accountId) {
        rooms.leave(roomCode, accountId);
    }

    @PutMapping("/{roomCode}/ready")
    public RoomResponse ready(@PathVariable String roomCode, @RequestHeader(CompetitionHeaders.USER_ID) UUID accountId,
            @Valid @RequestBody ReadyRequest request) {
        return rooms.ready(roomCode, accountId, request.ready());
    }

    @DeleteMapping("/{roomCode}/members/{targetId}")
    public RoomResponse kick(@PathVariable String roomCode, @PathVariable UUID targetId,
            @RequestHeader(CompetitionHeaders.USER_ID) UUID accountId) {
        return rooms.kick(roomCode, accountId, targetId);
    }

    @PutMapping("/{roomCode}/owner")
    public RoomResponse transferOwner(@PathVariable String roomCode,
            @RequestHeader(CompetitionHeaders.USER_ID) UUID accountId, @Valid @RequestBody RoomMemberRequest request) {
        return rooms.transferOwner(roomCode, accountId, request.accountId());
    }

    @PostMapping("/{roomCode}/invitations")
    public void invite(@PathVariable String roomCode, @RequestHeader(CompetitionHeaders.USER_ID) UUID accountId,
            @Valid @RequestBody RoomMemberRequest request) {
        rooms.invite(roomCode, accountId, request.accountId());
    }

    @PostMapping("/{roomCode}/start")
    public RoomResponse start(@PathVariable String roomCode, @RequestHeader(CompetitionHeaders.USER_ID) UUID accountId) {
        return rooms.start(roomCode, accountId);
    }
}
