package com.msspoker.matchmakingservice.util;

import java.util.UUID;

public final class MatchmakingIds {
    private MatchmakingIds() {
    }

    public static UUID newProposalId() {
        return UUID.randomUUID();
    }
}
