package com.msspoker.gameservice.api;

import com.msspoker.gameservice.engine.GameMode;
import com.msspoker.gameservice.engine.TableRules;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class GameDtos {
    private GameDtos() {
    }

    public record Settings(@Positive long smallBlind, @Positive long bigBlind,
                           @Min(1) @Max(TableRules.MAX_CHIPS) long startingChips,
                           @Min(1) @Max(300) int turnTimeSeconds) {
        @AssertTrue(message = "Blind lớn phải lớn hơn blind nhỏ và không vượt chip khởi điểm.")
        public boolean isBlindsValid() {
            return bigBlind > smallBlind && startingChips >= bigBlind;
        }
    }

    public record CreateTable(@NotNull GameMode mode, @NotNull UUID sourceId,
                              @NotNull @Size(min = 2, max = TableRules.MAX_SEATS) List<@NotNull UUID> playerIds,
                              @Valid Settings settings, @PositiveOrZero long entryFee) {
        @AssertTrue(message = "Danh sách người chơi không được trùng tài khoản.")
        public boolean isPlayersUnique() {
            return playerIds == null || new java.util.HashSet<>(playerIds).size() == playerIds.size();
        }

        @AssertTrue(message = "Trận Normal và Custom không có phí vào.")
        public boolean isEntryFeeValid() {
            return (mode != GameMode.NORMAL && mode != GameMode.CUSTOM) || entryFee == 0;
        }
    }

    public record TableCreated(UUID tableId, UUID matchId) {
    }

    public record DevTable(UUID tableId, UUID matchId, List<UUID> humanIds, List<UUID> botIds) {
    }

    public record MatchSettings(UUID id, GameMode mode, long smallBlind, long bigBlind, long startingChips,
                                int turnTimeSeconds, int minPlayers, int maxPlayers) {
    }

    public record UpdateSettings(@Valid @NotNull Settings settings,
                                 @Min(2) @Max(TableRules.MAX_SEATS) int minPlayers,
                                 @Min(2) @Max(TableRules.MAX_SEATS) int maxPlayers) {
        @AssertTrue(message = "Số người tối thiểu không được lớn hơn tối đa.")
        public boolean isPlayerRangeValid() {
            return minPlayers <= maxPlayers;
        }
    }

    public record Placement(UUID accountId, int place, long finalChips) {
    }

    public record MatchView(UUID id, UUID tableId, GameMode mode, UUID sourceId, long entryFee,
                            String status, Instant startedAt, Instant finishedAt,
                            List<Placement> placements) {
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }
}
