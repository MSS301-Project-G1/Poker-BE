package com.msspoker.rankingservice.service;

import com.msspoker.common.event.AccountRegisteredEvent;
import com.msspoker.rankingservice.dto.event.MatchFinishedEvent;
import com.msspoker.rankingservice.dto.event.PlacementEvent;
import com.msspoker.rankingservice.entity.EloHistoryEntity;
import com.msspoker.rankingservice.entity.PlayerEloEntity;
import com.msspoker.rankingservice.entity.RewardCreditEntity;
import com.msspoker.rankingservice.enums.GameMode;
import com.msspoker.rankingservice.enums.Tier;
import com.msspoker.rankingservice.repository.EloHistoryRepository;
import com.msspoker.rankingservice.repository.PlayerEloRepository;
import com.msspoker.rankingservice.repository.RewardCreditRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RatingServiceTest {
    @Autowired RatingService rating;
    @Autowired SeasonService seasons;
    @Autowired RewardDispatcher dispatcher;
    @Autowired PlayerEloRepository players;
    @Autowired EloHistoryRepository history;
    @Autowired RewardCreditRepository rewards;

    @BeforeEach
    void clean() {
        rewards.deleteAll();
        history.deleteAll();
        players.deleteAll();
    }

    @Test
    void registrationCreatesOneNewbieRecordEvenWhenRedelivered() {
        UUID account = UUID.randomUUID();
        rating.accountRegistered(new AccountRegisteredEvent(account));
        rating.accountRegistered(new AccountRegisteredEvent(account));

        PlayerEloEntity player = player(account);
        assertEquals(1, players.count());
        assertEquals(0, player.getCurrentElo());
        assertEquals(Tier.NEWBIE, player.getTier());
        assertEquals(seasons.active().getId(), player.getSeasonId());
        assertThrows(IllegalArgumentException.class, () -> rating.accountRegistered(new AccountRegisteredEvent(null)));
    }

    @Test
    void rankMatchAppliesSpecTableFloorsAtZeroAndQueuesTopThreeRewards() {
        List<UUID> accounts = accounts(6);
        accounts.forEach(id -> rating.accountRegistered(new AccountRegisteredEvent(id)));
        var event = rank(accounts, 100);

        rating.matchFinished(event);

        assertArrayEquals(new int[]{20, 10, 5, 0, 0, 0}, accounts.stream().mapToInt(id -> player(id).getCurrentElo()).toArray());
        List<EloHistoryEntity> rows = history.findByMatchIdOrderByPlace(event.matchId());
        // Applied deltas, not table values, once the Elo floor is reached.
        assertArrayEquals(new int[]{20, 10, 5, 0, 0, 0}, rows.stream().mapToInt(EloHistoryEntity::getDelta).toArray());
        assertTrue(rows.stream().allMatch(row -> row.getEloAfter() == player(row.getAccountId()).getCurrentElo()));

        List<RewardCreditEntity> credits = rewards.findByMatchIdOrderByPlace(event.matchId());
        assertEquals(List.of(300L, 200L, 100L), credits.stream().map(RewardCreditEntity::getAmount).toList());
        assertEquals("rank-reward:" + event.matchId() + ":" + accounts.getFirst(), credits.getFirst().getIdempotencyKey());
        assertTrue(credits.stream().allMatch(credit -> credit.getCreditedAt() == null));

        PlayerEloEntity winner = player(accounts.getFirst());
        PlayerEloEntity last = player(accounts.getLast());
        assertEquals(1, winner.getWinStreak());
        assertEquals(0, winner.getLossStreak());
        assertEquals(1, last.getLossStreak());
        assertEquals(0, last.getWinStreak());
        assertEquals(1, winner.getMatchesPlayed());
        assertEquals(event.finishedAt(), winner.getLastMatchAt());
    }

    @Test
    void redeliveredMatchIsIgnored() {
        List<UUID> accounts = accounts(3);
        var event = rank(accounts, 100);
        rating.matchFinished(event);
        rating.matchFinished(event);

        assertEquals(20, player(accounts.getFirst()).getCurrentElo());
        assertEquals(3, history.findByMatchIdOrderByPlace(event.matchId()).size());
        assertEquals(3, rewards.count());
    }

    @Test
    void twoPlayerTableUsesSpecTableByPlace() {
        List<UUID> accounts = accounts(2);
        rating.matchFinished(rank(accounts, 100));

        assertEquals(20, player(accounts.get(0)).getCurrentElo());
        assertEquals(10, player(accounts.get(1)).getCurrentElo());
        assertEquals(List.of(300L, 200L), rewards.findAll().stream().map(RewardCreditEntity::getAmount).sorted((a, b) -> Long.compare(b, a)).toList());
    }

    @Test
    void streaksPeakAndTierFollowConsecutiveResults() {
        List<UUID> accounts = accounts(6);
        UUID climber = accounts.getFirst();
        rating.accountRegistered(new AccountRegisteredEvent(climber));
        PlayerEloEntity seeded = player(climber);
        seeded.setCurrentElo(95);
        seeded.setPeakElo(95);
        players.save(seeded);

        rating.matchFinished(rank(accounts, 0));
        PlayerEloEntity afterWin = player(climber);
        assertEquals(115, afterWin.getCurrentElo());
        assertEquals(Tier.IRON, afterWin.getTier());

        List<UUID> reversed = new ArrayList<>(accounts.reversed());
        rating.matchFinished(rank(reversed, 0));
        PlayerEloEntity afterLoss = player(climber);
        assertEquals(100, afterLoss.getCurrentElo());
        assertEquals(115, afterLoss.getPeakElo());
        assertEquals(0, afterLoss.getWinStreak());
        assertEquals(1, afterLoss.getLossStreak());
        assertEquals(2, afterLoss.getMatchesPlayed());
        assertEquals(0, rewards.count());
    }

    @Test
    void nonRankModesAndInvalidResultsDoNotChangeElo() {
        List<UUID> accounts = accounts(3);
        for (GameMode mode : List.of(GameMode.NORMAL, GameMode.CUSTOM, GameMode.TOURNAMENT)) {
            var base = rank(accounts, 0);
            rating.matchFinished(new MatchFinishedEvent(base.matchId(), base.tableId(), mode, base.sourceId(),
                    0, base.placements(), base.startedAt(), base.finishedAt()));
        }
        assertEquals(0, players.count());

        var duplicatePlace = new MatchFinishedEvent(UUID.randomUUID(), UUID.randomUUID(), GameMode.RANK, UUID.randomUUID(), 100,
                List.of(new PlacementEvent(accounts.get(0), 1, 0), new PlacementEvent(accounts.get(1), 1, 0)),
                Instant.now(), Instant.now());
        assertThrows(IllegalArgumentException.class, () -> rating.matchFinished(duplicatePlace));
        var duplicateAccount = new MatchFinishedEvent(UUID.randomUUID(), UUID.randomUUID(), GameMode.RANK, UUID.randomUUID(), 100,
                List.of(new PlacementEvent(accounts.get(0), 1, 0), new PlacementEvent(accounts.get(0), 2, 0)),
                Instant.now(), Instant.now());
        assertThrows(IllegalArgumentException.class, () -> rating.matchFinished(duplicateAccount));
        assertEquals(0, players.count());
        assertEquals(0, history.count());
    }

    @Test
    void dispatcherCreditsPendingRewardsOnce() {
        rating.matchFinished(rank(accounts(3), 100));
        dispatcher.dispatchPending();
        dispatcher.dispatchPending();

        List<RewardCreditEntity> credits = rewards.findAll();
        assertEquals(3, credits.size());
        assertTrue(credits.stream().allMatch(credit -> credit.getCreditedAt() != null && credit.getAttempts() == 1));
    }

    static List<UUID> accounts(int count) {
        return IntStream.range(0, count).mapToObj(i -> UUID.randomUUID()).toList();
    }

    /** accounts are listed by finishing place. */
    static MatchFinishedEvent rank(List<UUID> accounts, long entryFee) {
        List<PlacementEvent> placements = IntStream.range(0, accounts.size())
                .mapToObj(i -> new PlacementEvent(accounts.get(i), i + 1, i == 0 ? 1000L * accounts.size() : 0)).toList();
        Instant finished = Instant.parse("2026-10-13T08:00:00Z");
        return new MatchFinishedEvent(UUID.randomUUID(), UUID.randomUUID(), GameMode.RANK, UUID.randomUUID(), entryFee,
                placements, finished.minusSeconds(600), finished);
    }

    private PlayerEloEntity player(UUID accountId) {
        return players.findBySeasonIdAndAccountIdAndIsDeletedFalse(seasons.active().getId(), accountId).orElseThrow();
    }
}
