package com.msspoker.competitionservice.service;

import com.msspoker.competitionservice.dto.event.MatchFinishedEvent;
import com.msspoker.competitionservice.dto.event.PlacementEvent;
import com.msspoker.competitionservice.dto.request.CreateTournamentRequest;
import com.msspoker.competitionservice.dto.response.RankedPlayerResponse;
import com.msspoker.competitionservice.entity.TournamentEntity;
import com.msspoker.competitionservice.entity.TournamentSeatEntity;
import com.msspoker.competitionservice.entity.TournamentTableEntity;
import com.msspoker.competitionservice.enums.GameMode;
import com.msspoker.competitionservice.enums.TournamentType;
import com.msspoker.competitionservice.repository.*;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/** Shared fixtures for tournament tests that run against the test H2 database. */
public final class TournamentTestSupport {
    private TournamentTestSupport() {
    }

    public static void clean(TournamentSeatRepository seats, TournamentTableRepository tables,
                             TournamentEntryRepository entries, TournamentRepository tournaments,
                             WalletCreditRepository credits, ItemGrantRepository itemGrants, OutboxRepository outbox) {
        seats.deleteAll();
        tables.deleteAll();
        entries.deleteAll();
        tournaments.deleteAll();
        credits.deleteAll();
        itemGrants.deleteAll();
        outbox.deleteAll();
    }

    public static CreateTournamentRequest open(long entryFee, Integer slots) {
        return new CreateTournamentRequest("Giải Mở tuần", TournamentType.OPEN,
                Instant.now().plus(1, ChronoUnit.HOURS), entryFee, slots, null, null);
    }

    public static CreateTournamentRequest elite(Integer slots, Long rewardCoins, UUID rewardProductId) {
        return new CreateTournamentRequest("Giải Cao thủ tháng", TournamentType.ELITE,
                Instant.now().plus(1, ChronoUnit.HOURS), 0, slots, rewardCoins, rewardProductId);
    }

    /** ranking-service top list in the given order, starting at position 1. */
    public static List<RankedPlayerResponse> ranked(List<UUID> accountIds) {
        return IntStream.range(0, accountIds.size()).mapToObj(i -> new RankedPlayerResponse(i + 1, accountIds.get(i),
                2000 - i * 10, "GRAND_CHAMPION", 0, 30)).toList();
    }

    /**
     * Moves the tournament's timeline earlier as if the given minutes had passed: start time, invitation
     * marks and entry times shift together, so confirmation deadlines keep their meaning.
     */
    public static void travel(TournamentRepository tournaments, TournamentEntryRepository entries, UUID tournamentId,
                              long minutes) {
        Duration shift = Duration.ofMinutes(minutes);
        TournamentEntity tournament = tournaments.findById(tournamentId).orElseThrow();
        tournament.setStartAt(tournament.getStartAt().minus(shift));
        if (tournament.getInvitesSentAt() != null) tournament.setInvitesSentAt(tournament.getInvitesSentAt().minus(shift));
        if (tournament.getReplacementsSentAt() != null) {
            tournament.setReplacementsSentAt(tournament.getReplacementsSentAt().minus(shift));
        }
        tournaments.save(tournament);
        entries.findByTournamentIdOrderByRegisteredAtAscIdAsc(tournamentId).forEach(entry -> {
            entry.setRegisteredAt(entry.getRegisteredAt().minus(shift));
            entries.save(entry);
        });
    }

    /** Seat order is the finishing order: seat 0 wins. */
    public static MatchFinishedEvent finished(TournamentTableEntity table, List<TournamentSeatEntity> seats) {
        List<PlacementEvent> placements = IntStream.range(0, seats.size())
                .mapToObj(i -> new PlacementEvent(seats.get(i).getAccountId(), i + 1, i == 0 ? 8000 : 0)).toList();
        return new MatchFinishedEvent(UUID.randomUUID(), table.getTableId(), GameMode.TOURNAMENT, table.getId(), 0,
                placements, Instant.now().minusSeconds(600), Instant.now());
    }
}
