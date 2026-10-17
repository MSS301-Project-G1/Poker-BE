package com.msspoker.competitionservice.service;

import com.msspoker.competitionservice.dto.request.ShopGrantRequest;
import com.msspoker.competitionservice.dto.request.WalletTransactionRequest;
import com.msspoker.competitionservice.dto.response.TableCreatedResponse;
import com.msspoker.competitionservice.dto.response.TournamentEntryResponse;
import com.msspoker.competitionservice.dto.response.TournamentResponse;
import com.msspoker.competitionservice.entity.OutboxEntity;
import com.msspoker.competitionservice.entity.TournamentEntryEntity;
import com.msspoker.competitionservice.entity.TournamentSeatEntity;
import com.msspoker.competitionservice.entity.TournamentTableEntity;
import com.msspoker.competitionservice.entity.WalletCreditEntity;
import com.msspoker.competitionservice.enums.*;
import com.msspoker.competitionservice.exception.CompetitionApiException;
import com.msspoker.competitionservice.exception.CompetitionExceptions;
import com.msspoker.competitionservice.repository.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.msspoker.competitionservice.service.TournamentTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class TournamentScheduleTest {
    @Autowired TournamentService tournaments;
    @Autowired TournamentScheduler scheduler;
    @Autowired WalletCreditDispatcher walletDispatcher;
    @Autowired ItemGrantDispatcher itemDispatcher;
    @Autowired TournamentRepository tournamentRepository;
    @Autowired TournamentEntryRepository entries;
    @Autowired TournamentTableRepository tables;
    @Autowired TournamentSeatRepository seats;
    @Autowired WalletCreditRepository credits;
    @Autowired ItemGrantRepository itemGrants;
    @Autowired OutboxRepository outbox;
    @Autowired JsonMapper json;
    @MockitoBean GameClient game;
    @MockitoBean WalletClient wallet;
    @MockitoBean ShopClient shop;
    @MockitoBean RankingClient ranking;

    final UUID admin = UUID.randomUUID();
    final List<UUID> leaderboard = IntStream.range(0, 12).mapToObj(i -> UUID.randomUUID()).toList();

    @BeforeEach
    void setup() {
        clean(seats, tables, entries, tournamentRepository, credits, itemGrants, outbox);
        when(game.createTable(any())).thenAnswer(call -> new TableCreatedResponse(UUID.randomUUID(), UUID.randomUUID()));
        when(ranking.top(anyInt())).thenAnswer(call -> ranked(leaderboard.subList(0,
                Math.min(call.<Integer>getArgument(0), leaderboard.size()))));
    }

    @Test
    void eliteInvitesTheTopReplacesNoShowsAndPaysThePodium() {
        UUID skin = UUID.randomUUID();
        UUID id = tournaments.create(admin, elite(8, 3000L, skin)).id();
        assertEquals(List.of(1500L, 900L, 600L), tournaments.get(id).prizes());
        scheduler.runDue();
        verify(ranking, never()).top(anyInt());

        travel(tournamentRepository, entries, id, 35);
        scheduler.runDue();
        scheduler.runDue();
        verify(ranking, times(1)).top(8);
        assertEquals(leaderboard.subList(0, 8), accounts(id, EntryStatus.INVITED));
        JsonNode invite = notification(0);
        assertEquals("TOURNAMENT_INVITE", invite.get("type").asString());
        assertEquals(8, invite.get("accountIds").size());
        assertEquals(id.toString(), invite.get("data").get("tournamentId").asString());
        TournamentResponse invited = tournaments.get(id);
        assertEquals(invited.startAt().minusSeconds(600), invited.entries().getFirst().confirmBy());

        leaderboard.subList(0, 5).forEach(player -> tournaments.confirm(id, player));
        tournaments.confirm(id, leaderboard.getFirst());
        assertError(CompetitionError.COMP_FORBIDDEN, () -> tournaments.confirm(id, leaderboard.get(8)));
        assertError(CompetitionError.COMP_FORBIDDEN, () -> tournaments.register(id, leaderboard.get(8)));

        travel(tournamentRepository, entries, id, 16);
        assertError(CompetitionError.COMP_CONFLICT, () -> tournaments.confirm(id, leaderboard.get(5)));
        scheduler.runDue();
        verify(ranking).top(11);
        assertEquals(leaderboard.subList(5, 8), accounts(id, EntryStatus.EXPIRED));
        assertEquals(leaderboard.subList(8, 11), accounts(id, EntryStatus.INVITED));
        assertEquals(3, notification(1).get("accountIds").size());
        TournamentResponse replaced = tournaments.get(id);
        assertTrue(replaced.entries().stream().filter(e -> e.status() == EntryStatus.INVITED)
                .allMatch(e -> e.confirmBy().equals(replaced.startAt())));
        tournaments.confirm(id, leaderboard.get(8));
        tournaments.confirm(id, leaderboard.get(9));
        assertError(CompetitionError.COMP_CONFLICT, () -> tournaments.confirm(id, leaderboard.get(5)));

        travel(tournamentRepository, entries, id, 10);
        scheduler.runDue();
        TournamentResponse started = tournaments.get(id);
        assertEquals(TournamentStatus.FINAL, started.status());
        assertEquals(EntryStatus.EXPIRED, entry(started, leaderboard.get(10)).status());
        List<UUID> expected = new ArrayList<>(leaderboard.subList(0, 5));
        expected.addAll(leaderboard.subList(8, 10));
        assertEquals(expected.stream().sorted().toList(), started.tables().getFirst().seats().stream()
                .map(s -> s.accountId()).sorted().toList());
        verify(game, times(1)).createTable(any());

        TournamentTableEntity finalTable = tables.findByTournamentIdAndRoundOrderByTableIndexAsc(id, TournamentRound.FINAL).getFirst();
        List<TournamentSeatEntity> roster = seats.findByTournamentTableIdOrderBySeatIndex(finalTable.getId());
        tournaments.matchFinished(finished(finalTable, roster));
        tournaments.matchFinished(finished(finalTable, roster));
        assertEquals(TournamentStatus.FINISHED, tournaments.get(id).status());
        Map<UUID, Long> paid = credits.findAll().stream()
                .collect(Collectors.toMap(WalletCreditEntity::getAccountId, WalletCreditEntity::getAmount));
        assertEquals(Map.of(roster.get(0).getAccountId(), 1500L, roster.get(1).getAccountId(), 900L,
                roster.get(2).getAccountId(), 600L), paid);
        UUID champion = roster.getFirst().getAccountId();
        assertEquals(1, itemGrants.count());

        walletDispatcher.dispatchPending();
        itemDispatcher.dispatchPending();
        itemDispatcher.dispatchPending();
        ArgumentCaptor<WalletTransactionRequest> prizes = ArgumentCaptor.forClass(WalletTransactionRequest.class);
        verify(wallet, times(3)).credit(prizes.capture());
        assertTrue(prizes.getAllValues().stream().allMatch(p -> p.reason().equals("TOURNAMENT_PRIZE")
                && p.refId().equals(id.toString())
                && p.idempotencyKey().equals("tournament-prize:" + id + ":" + p.accountId())));
        verify(shop, times(1)).grant(new ShopGrantRequest(champion, skin, "tournament-item:" + id + ":" + champion));
        assertNotNull(itemGrants.findAll().getFirst().getGrantedAt());
    }

    @Test
    void rankingOutageDelaysInvitesAndTooFewConfirmationsCancel() {
        UUID id = tournaments.create(admin, elite(8, 1000L, null)).id();
        travel(tournamentRepository, entries, id, 35);
        doThrow(CompetitionExceptions.rankingUnavailable()).when(ranking).top(anyInt());
        scheduler.runDue();
        assertNull(tournamentRepository.findById(id).orElseThrow().getInvitesSentAt());
        assertTrue(entries.findByTournamentIdOrderByRegisteredAtAscIdAsc(id).isEmpty());

        doAnswer(call -> ranked(leaderboard.subList(0, 3))).when(ranking).top(anyInt());
        scheduler.runDue();
        assertEquals(3, accounts(id, EntryStatus.INVITED).size());
        leaderboard.subList(0, 3).forEach(player -> tournaments.confirm(id, player));

        travel(tournamentRepository, entries, id, 16);
        scheduler.runDue();
        assertNotNull(tournamentRepository.findById(id).orElseThrow().getReplacementsSentAt());
        assertEquals(3, entries.findByTournamentIdOrderByRegisteredAtAscIdAsc(id).size());

        travel(tournamentRepository, entries, id, 10);
        scheduler.runDue();
        assertEquals(TournamentStatus.CANCELLED, tournaments.get(id).status());
        assertEquals(0, credits.count());
        verify(game, never()).createTable(any());
    }

    @Test
    void openTournamentsStartOrCancelWithRefundsAtTheirStart() {
        UUID full = tournaments.create(admin, open(100, null)).id();
        UUID empty = tournaments.create(admin, open(100, null)).id();
        leaderboard.subList(0, 5).forEach(player -> tournaments.register(full, player));
        leaderboard.subList(5, 7).forEach(player -> tournaments.register(empty, player));
        assertEquals(500, tournaments.get(full).prizePool());
        assertEquals(List.of(250L, 150L, 100L), tournaments.get(full).prizes());

        scheduler.runDue();
        assertEquals(TournamentStatus.SCHEDULED, tournaments.get(full).status());
        travel(tournamentRepository, entries, full, 61);
        travel(tournamentRepository, entries, empty, 61);
        scheduler.runDue();
        scheduler.runDue();

        assertEquals(TournamentStatus.FINAL, tournaments.get(full).status());
        assertEquals(TableStatus.PLAYING, tournaments.get(full).tables().getFirst().status());
        assertEquals(TournamentStatus.CANCELLED, tournaments.get(empty).status());
        assertEquals(2, credits.count());
        assertTrue(credits.findAll().stream().allMatch(c -> c.getReason().equals("TOURNAMENT_REFUND") && c.getAmount() == 100));
        assertTrue(tournamentRepository.findDueToStart(TournamentStatus.SCHEDULED, Instant.now()).isEmpty());
    }

    private List<UUID> accounts(UUID tournamentId, EntryStatus status) {
        return entries.findByTournamentIdOrderByRegisteredAtAscIdAsc(tournamentId).stream()
                .filter(entry -> entry.getStatus() == status)
                .sorted(Comparator.comparingInt(entry -> leaderboard.indexOf(entry.getAccountId())))
                .map(TournamentEntryEntity::getAccountId).toList();
    }

    private TournamentEntryResponse entry(TournamentResponse tournament, UUID accountId) {
        return tournament.entries().stream().filter(e -> e.accountId().equals(accountId)).findFirst().orElseThrow();
    }

    private JsonNode notification(int index) {
        return outbox.findAll().stream().sorted(Comparator.comparing(OutboxEntity::getOccurredAt))
                .map(row -> json.readTree(row.getBody()).get("payload")).toList().get(index);
    }

    private void assertError(CompetitionError code, Executable action) {
        CompetitionApiException ex = assertThrows(CompetitionApiException.class, action);
        assertEquals(code, ex.getCode());
    }
}
