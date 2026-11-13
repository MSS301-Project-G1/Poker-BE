package com.msspoker.matchmakingservice.controller;

import com.msspoker.matchmakingservice.constant.MatchmakingConstants;
import com.msspoker.matchmakingservice.dto.response.NormalReconnectResponse;
import com.msspoker.matchmakingservice.dto.response.QueueStatusResponse;
import com.msspoker.matchmakingservice.service.RankMatchmakingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** API ghép Rank cho người chơi solo; service kiểm tra điều kiện và trạng thái tham gia. */
@RestController
@RequestMapping("/api/matchmaking/rank")
@Tag(name = "Rank matchmaking", description = "Ghép Rank theo Elo cố định; Party chưa được hỗ trợ.")
@RequiredArgsConstructor
public class RankMatchmakingController {
    private final RankMatchmakingService matchmaking;

    /** Đăng ký ghép Rank; service kiểm tra party, hạn chế tài khoản, phí vào và Elo. */
    @PostMapping("/join")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "joinRank", summary = "Join Rank")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Đã vào hàng chờ Rank"),
        @ApiResponse(
                responseCode = "402",
                description =
                        "MM_INSUFFICIENT_BALANCE: COIN không đủ phí vào Rank, chưa đưa vào queue"),
        @ApiResponse(
                responseCode = "409",
                description = "Đã ở queue/proposal/trận hoặc đang thuộc Party"),
        @ApiResponse(responseCode = "502", description = "Wallet, Ranking hoặc Game không khả dụng")
    })
    public QueueStatusResponse join(
            @Parameter(
                            name = MatchmakingConstants.USER_ID_HEADER,
                            required = true,
                            in = ParameterIn.HEADER)
                    @RequestHeader(MatchmakingConstants.USER_ID_HEADER)
                    UUID accountId) {
        return matchmaking.join(accountId);
    }

    /** Hủy hàng chờ Rank theo trạng thái hiện tại của người chơi. */
    @DeleteMapping("/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "leaveRank", summary = "Leave Rank")
    public void leave(
            @Parameter(
                            name = MatchmakingConstants.USER_ID_HEADER,
                            required = true,
                            in = ParameterIn.HEADER)
                    @RequestHeader(MatchmakingConstants.USER_ID_HEADER)
                    UUID accountId) {
        matchmaking.leave(accountId);
    }

    /** Trả trạng thái Rank để frontend biết lúc nào cần hiển thị đề xuất trận. */
    @GetMapping("/status")
    @Operation(operationId = "getRankStatus", summary = "Rank status")
    public QueueStatusResponse status(
            @Parameter(
                            name = MatchmakingConstants.USER_ID_HEADER,
                            required = true,
                            in = ParameterIn.HEADER)
                    @RequestHeader(MatchmakingConstants.USER_ID_HEADER)
                    UUID accountId) {
        return matchmaking.status(accountId);
    }

    /** Tìm bàn Rank còn hoạt động để người chơi kết nối lại. */
    @GetMapping("/reconnect")
    @Operation(operationId = "reconnectRank", summary = "Reconnect bàn Rank")
    public NormalReconnectResponse reconnect(
            @Parameter(
                            name = MatchmakingConstants.USER_ID_HEADER,
                            required = true,
                            in = ParameterIn.HEADER)
                    @RequestHeader(MatchmakingConstants.USER_ID_HEADER)
                    UUID accountId) {
        return matchmaking.reconnect(accountId);
    }
}
