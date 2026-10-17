package com.msspoker.matchmakingservice.model;

import com.msspoker.matchmakingservice.enums.MatchMode;

import java.util.UUID;

public record InGameSession(
        MatchMode mode,
        UUID proposalId,
        UUID tableId,
        UUID matchId,
        String host,
        int port,
        String websocketPath
) {
}
