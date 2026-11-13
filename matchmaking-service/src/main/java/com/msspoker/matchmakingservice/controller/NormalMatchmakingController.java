package com.msspoker.matchmakingservice.controller;

import com.msspoker.matchmakingservice.constant.MatchmakingConstants;
import com.msspoker.matchmakingservice.dto.response.QueueStatusResponse;
import com.msspoker.matchmakingservice.dto.response.NormalReconnectResponse;
import com.msspoker.matchmakingservice.service.NormalMatchmakingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** API ghép Normal; danh tính lấy từ Gateway, nghiệp vụ được xử lý ở service. */
@RestController
@RequestMapping("/api/matchmaking/normal")
@Tag(name = "Normal matchmaking", description = "Ghép trận thường, miễn phí và không dùng Ranking/Wallet.")
public class NormalMatchmakingController {
    private final NormalMatchmakingService matchmaking;

    public NormalMatchmakingController(NormalMatchmakingService matchmaking) {
        this.matchmaking = matchmaking;
    }

    /** Đưa người chơi hoặc party do họ làm chủ vào hàng chờ Normal. */
    @PostMapping("/join")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "joinNormal", summary = "Join Normal", description = "Đưa người chơi solo hoặc toàn bộ Party nếu người gọi là chủ Party vào hàng chờ NORMAL.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Đã vào hàng chờ"),
            @ApiResponse(responseCode = "401", description = "Thiếu X-User-Id", content = @Content),
            @ApiResponse(responseCode = "409", description = "Đã ở queue, đang tạo bàn hoặc đang trong trận", content = @Content)
    })
    public QueueStatusResponse join(
            @Parameter(name = MatchmakingConstants.USER_ID_HEADER, description = "UUID tài khoản do API Gateway lấy từ JWT và gắn vào request", required = true, in = ParameterIn.HEADER, example = "11111111-1111-1111-1111-111111111111")
            @RequestHeader(MatchmakingConstants.USER_ID_HEADER) UUID accountId
    ) {
        return matchmaking.join(accountId);
    }

    /** Rời hàng chờ; service kiểm tra quyền và trạng thái đang ghép. */
    @DeleteMapping("/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "leaveNormal", summary = "Leave Normal", description = "Rời hàng chờ NORMAL; chủ Party sẽ hủy queue cho toàn bộ nhóm nếu chưa bị reserve.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đã rời hàng chờ"),
            @ApiResponse(responseCode = "401", description = "Thiếu X-User-Id", content = @Content),
            @ApiResponse(responseCode = "404", description = "Người chơi không ở trong hàng chờ", content = @Content),
            @ApiResponse(responseCode = "409", description = "Queue đang được ghép và không thể hủy", content = @Content)
    })
    public void leave(
            @Parameter(name = MatchmakingConstants.USER_ID_HEADER, description = "UUID tài khoản do API Gateway lấy từ JWT và gắn vào request", required = true, in = ParameterIn.HEADER, example = "11111111-1111-1111-1111-111111111111")
            @RequestHeader(MatchmakingConstants.USER_ID_HEADER) UUID accountId
    ) {
        matchmaking.leave(accountId);
    }

    /** Trả trạng thái hàng chờ và thông tin bàn để frontend thăm dò. */
    @GetMapping("/status")
    @Operation(operationId = "getNormalStatus", summary = "Normal status", description = "Trả về NONE, WAITING, COUNTDOWN, MATCHING, CREATING_TABLE, READY hoặc FAILED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Trạng thái hiện tại", content = @Content(
                    schema = @Schema(implementation = QueueStatusResponse.class),
                    examples = @ExampleObject(value = "{\"status\":\"COUNTDOWN\",\"mode\":\"NORMAL\",\"joinedAt\":\"2026-10-15T03:00:00Z\",\"position\":1,\"playerCount\":3,\"minPlayers\":2,\"maxPlayers\":6,\"matchStartDeadline\":\"2026-10-15T03:00:10Z\",\"proposalId\":null,\"tableId\":null,\"matchId\":null}")
            )),
            @ApiResponse(responseCode = "401", description = "Thiếu X-User-Id", content = @Content)
    })
    public QueueStatusResponse status(
            @Parameter(name = MatchmakingConstants.USER_ID_HEADER, description = "UUID tài khoản do API Gateway lấy từ JWT và gắn vào request", required = true, in = ParameterIn.HEADER, example = "11111111-1111-1111-1111-111111111111")
            @RequestHeader(MatchmakingConstants.USER_ID_HEADER) UUID accountId
    ) {
        return matchmaking.status(accountId);
    }

    /** Kiểm tra bàn cũ với Game trước khi trả thông tin kết nối lại. */
    @GetMapping("/reconnect")
    @Operation(operationId = "reconnectNormal", summary = "Reconnect Normal", description = "Kiểm tra trạng thái IN_GAME trong Redis và trả host/port của bàn cũ nếu Game vẫn xác nhận bàn đang hoạt động.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Kết quả tìm bàn đang hoạt động", content = @Content(
                    schema = @Schema(implementation = NormalReconnectResponse.class),
                    examples = @ExampleObject(value = "{\"reconnectable\":true,\"status\":\"IN_GAME\",\"mode\":\"NORMAL\",\"proposalId\":\"22222222-2222-2222-2222-222222222222\",\"tableId\":\"33333333-3333-3333-3333-333333333333\",\"matchId\":\"44444444-4444-4444-4444-444444444444\",\"host\":\"localhost\",\"port\":8082,\"websocketPath\":\"/ws/game\"}")
            )),
            @ApiResponse(responseCode = "401", description = "Thiếu X-User-Id", content = @Content),
            @ApiResponse(responseCode = "502", description = "Không kiểm tra được game-service", content = @Content)
    })
    public NormalReconnectResponse reconnect(
            @Parameter(name = MatchmakingConstants.USER_ID_HEADER, description = "UUID tài khoản do API Gateway lấy từ JWT và gắn vào request", required = true, in = ParameterIn.HEADER, example = "11111111-1111-1111-1111-111111111111")
            @RequestHeader(MatchmakingConstants.USER_ID_HEADER) UUID accountId
    ) {
        return matchmaking.reconnect(accountId);
    }
}
