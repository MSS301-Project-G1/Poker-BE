package com.msspoker.matchmakingservice.controller;

import com.msspoker.matchmakingservice.constant.MatchmakingConstants;
import com.msspoker.matchmakingservice.dto.response.QueueStatusResponse;
import com.msspoker.matchmakingservice.dto.response.NormalReconnectResponse;
import com.msspoker.matchmakingservice.service.RankMatchmakingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/matchmaking/rank")
@Tag(name = "Rank matchmaking", description = "Ghép Rank theo Elo cố định; Party chưa được hỗ trợ.")
public class RankMatchmakingController {
    private final RankMatchmakingService matchmaking;

    public RankMatchmakingController(RankMatchmakingService matchmaking) {
        this.matchmaking = matchmaking;
    }

    @PostMapping("/join")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "joinRank", summary = "Join Rank")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Đã vào hàng chờ Rank"),
            @ApiResponse(responseCode = "409", description = "Đã ở queue/proposal/trận hoặc đang thuộc Party"),
            @ApiResponse(responseCode = "502", description = "Ranking hoặc Game không khả dụng")
    })
    public QueueStatusResponse join(
            @Parameter(name = MatchmakingConstants.USER_ID_HEADER, required = true, in = ParameterIn.HEADER)
            @RequestHeader(MatchmakingConstants.USER_ID_HEADER) UUID accountId
    ) {
        return matchmaking.join(accountId);
    }

    @DeleteMapping("/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "leaveRank", summary = "Leave Rank")
    public void leave(
            @Parameter(name = MatchmakingConstants.USER_ID_HEADER, required = true, in = ParameterIn.HEADER)
            @RequestHeader(MatchmakingConstants.USER_ID_HEADER) UUID accountId
    ) {
        matchmaking.leave(accountId);
    }

    @GetMapping("/status")
    @Operation(operationId = "getRankStatus", summary = "Rank status")
    public QueueStatusResponse status(
            @Parameter(name = MatchmakingConstants.USER_ID_HEADER, required = true, in = ParameterIn.HEADER)
            @RequestHeader(MatchmakingConstants.USER_ID_HEADER) UUID accountId
    ) {
        return matchmaking.status(accountId);
    }

    @GetMapping("/reconnect")
    @Operation(operationId = "reconnectRank", summary = "Reconnect bàn Rank")
    public NormalReconnectResponse reconnect(
            @Parameter(name = MatchmakingConstants.USER_ID_HEADER, required = true, in = ParameterIn.HEADER)
            @RequestHeader(MatchmakingConstants.USER_ID_HEADER) UUID accountId
    ) {
        return matchmaking.reconnect(accountId);
    }
}
