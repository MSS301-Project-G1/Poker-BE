package com.msspoker.matchmakingservice.dto.request;

import com.msspoker.matchmakingservice.enums.MatchMode;

import java.util.List;
import java.util.UUID;

public record CreateTableRequest(
        MatchMode mode,
        UUID sourceId,
        List<UUID> playerIds,
        Object settings,
        long entryFee
) {
    public static CreateTableRequest normal(UUID proposalId, List<UUID> playerIds) {
        return new CreateTableRequest(MatchMode.NORMAL, proposalId, List.copyOf(playerIds), null, 0);
    }

    public static CreateTableRequest rank(UUID proposalId, List<UUID> playerIds, long entryFee) {
        return new CreateTableRequest(MatchMode.RANK, proposalId, List.copyOf(playerIds), null, entryFee);
    }
}
