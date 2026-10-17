package com.msspoker.matchmakingservice.dto.response;

import com.msspoker.matchmakingservice.enums.MatchMode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Thông tin để client nối lại bàn đang hoạt động")
public record NormalReconnectResponse(
        @Schema(description = "Có bàn đang hoạt động để nối lại hay không", example = "true") boolean reconnectable,
        @Schema(description = "Trạng thái lưu trong Redis", allowableValues = {"IN_GAME", "NONE"}, example = "IN_GAME") String status,
        @Schema(description = "Mode của API reconnect", example = "NORMAL") MatchMode mode,
        @Schema(description = "Proposal đã tạo bàn") UUID proposalId,
        @Schema(description = "ID bàn đang hoạt động") UUID tableId,
        @Schema(description = "ID trận đang hoạt động") UUID matchId,
        @Schema(description = "Host/IP public để client nối lại", example = "localhost") String host,
        @Schema(description = "Port public để client nối lại", example = "8082") Integer port,
        @Schema(description = "Đường dẫn WebSocket của Game", example = "/ws/game") String websocketPath
) {
    public static NormalReconnectResponse none() {
        return none(MatchMode.NORMAL);
    }

    public static NormalReconnectResponse none(MatchMode mode) {
        return new NormalReconnectResponse(false, "NONE", mode, null, null, null, null, null, null);
    }

    public static NormalReconnectResponse active(
            UUID proposalId,
            UUID tableId,
            UUID matchId,
            String host,
            int port,
            String websocketPath
    ) {
        return active(MatchMode.NORMAL, proposalId, tableId, matchId, host, port, websocketPath);
    }

    public static NormalReconnectResponse active(
            MatchMode mode,
            UUID proposalId,
            UUID tableId,
            UUID matchId,
            String host,
            int port,
            String websocketPath
    ) {
        return new NormalReconnectResponse(
                true, "IN_GAME", mode, proposalId, tableId, matchId, host, port, websocketPath
        );
    }
}
