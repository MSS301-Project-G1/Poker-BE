package com.msspoker.matchmakingservice.dto.response;

import com.msspoker.matchmakingservice.enums.MatchMode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

@Schema(description = "Trạng thái hàng chờ hoặc kết quả tạo bàn gần nhất")
public record QueueStatusResponse(
        @Schema(description = "Trạng thái workflow", allowableValues = {"NONE", "WAITING", "COUNTDOWN", "MATCHING", "PROPOSED", "CREATING_TABLE", "READY", "FAILED"}, example = "COUNTDOWN") String status,
        @Schema(description = "Chế độ hàng chờ", example = "NORMAL") MatchMode mode,
        @Schema(description = "Thời điểm vào hàng chờ theo UTC") Instant joinedAt,
        @Schema(description = "Vị trí hiện tại trong hàng chờ, bắt đầu từ 1", example = "1") Long position,
        @Schema(description = "Số người hiện đang chờ trong queue", example = "3") Long playerCount,
        @Schema(description = "Số người tối thiểu để bắt đầu countdown", example = "2") Integer minPlayers,
        @Schema(description = "Đủ số người này thì tạo trận ngay", example = "6") Integer maxPlayers,
        @Schema(description = "Deadline UTC để tạo trận với 2–5 người") Instant matchStartDeadline,
        @Schema(description = "Deadline UTC để chấp nhận proposal Rank") Instant acceptDeadline,
        @Schema(description = "Proposal đang xử lý") UUID proposalId,
        @Schema(description = "Bàn do Game Service tạo khi READY") UUID tableId,
        @Schema(description = "Match do Game Service tạo khi READY") UUID matchId,
        @Schema(description = "UUID người đang chờ, tối đa maxPlayers; trước proposal đây là danh sách xem trước hàng chờ, chưa phải ghế đã chốt") List<UUID> playerIds
) {
    public static QueueStatusResponse none() {
        return new QueueStatusResponse("NONE", null, null, null, null, null, null, null, null, null, null, null, List.of());
    }
}
