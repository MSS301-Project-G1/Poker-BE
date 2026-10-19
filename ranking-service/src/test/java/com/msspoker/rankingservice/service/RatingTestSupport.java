package com.msspoker.rankingservice.service;

import com.msspoker.rankingservice.dto.event.MatchFinishedEvent;
import com.msspoker.rankingservice.dto.event.PlacementEvent;
import com.msspoker.rankingservice.enums.GameMode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/** Shared RANK match fixtures for rating and reward dispatcher tests. */
public final class RatingTestSupport {
    private RatingTestSupport() {
    }

    public static List<UUID> accounts(int count) {
        return IntStream.range(0, count).mapToObj(i -> UUID.randomUUID()).toList();
    }

    /** accounts are listed by finishing place. */
    public static MatchFinishedEvent rank(List<UUID> accounts, long entryFee) {
        List<PlacementEvent> placements = IntStream.range(0, accounts.size())
                .mapToObj(i -> new PlacementEvent(accounts.get(i), i + 1, i == 0 ? 1000L * accounts.size() : 0)).toList();
        Instant finished = Instant.parse("2026-10-13T08:00:00Z");
        return new MatchFinishedEvent(UUID.randomUUID(), UUID.randomUUID(), GameMode.RANK, UUID.randomUUID(), entryFee,
                placements, finished.minusSeconds(600), finished);
    }
}
