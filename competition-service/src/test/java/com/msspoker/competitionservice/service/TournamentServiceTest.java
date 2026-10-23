package com.msspoker.competitionservice.service;

import com.msspoker.competitionservice.dto.request.CreateTableRequest;
import com.msspoker.competitionservice.dto.request.CreateTournamentRequest;
import com.msspoker.competitionservice.dto.request.WalletTransactionRequest;
import com.msspoker.competitionservice.dto.response.TableCreatedResponse;
import com.msspoker.competitionservice.dto.response.TournamentResponse;
import com.msspoker.competitionservice.entity.TournamentSeatEntity;
import com.msspoker.competitionservice.entity.TournamentTableEntity;
import com.msspoker.competitionservice.entity.WalletCreditEntity;
import com.msspoker.competitionservice.enums.*;
import com.msspoker.competitionservice.exception.CompetitionApiException;
import com.msspoker.competitionservice.exception.CompetitionExceptions;
import com.msspoker.competitionservice.repository.*;
import com.msspoker.competitionservice.service.client.GameClient;
import com.msspoker.competitionservice.service.client.WalletClient;
import com.msspoker.competitionservice.service.job.TournamentTableLauncher;
import com.msspoker.competitionservice.service.job.WalletCreditDispatcher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.msspoker.competitionservice.service.TournamentTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class TournamentServiceTest {
    @Autowired TournamentService tournaments;
    @Autowired TournamentTableLauncher launcher;
    @Autowired WalletCreditDispatcher dispatcher;
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

    final UUID admin = UUID.randomUUID();

    @BeforeEach
    void setup() {
        clean(seats, tables, entries, tournamentRepository, credits, itemGrants, outbox);
        when(game.createTable(any())).thenAnswer(call -> new TableCreatedResponse(UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void createUsesDefaultFieldAndRejectsOversizedOnes() {
        TournamentResponse created = tournaments.create(admin, open(100, null));
        assertEquals(32, created.slots());
        assertEquals(TournamentStatus.SCHEDULED, created.status());
        assertTrue(created.entries().isEmpty() && created.tables().isEmpty());
        assertError(CompetitionError.COMP_INVALID_REQUEST, () -> tournaments.create(admin, open(100, 33)));
    }

    @Test
    void listSplitsUpcomingFromEndedTournaments() {
        Instant now = Instant.now();
        List<UUID> ids = IntStream.rangeClosed(1, 3).mapToObj(hours -> tournaments.create(admin, new CreateTournamentRequest(
                "Giải " + hours, TournamentType.OPEN, now.plusSeconds(hours * 3600L), 0, null, null, null)).id()).toList();
        tournaments.cancel(ids.get(0));
        tournaments.cancel(ids.get(2));

        assertEquals(List.of(ids.get(1)), tournaments.list(false, 0, 20).content().stream().map(t -> t.id()).toList());
        assertEquals(List.of(ids.get(2), ids.get(0)), tournaments.list(true, 0, 20).content().stream().map(t -> t.id()).toList());
    }

    @Test
    void registrationChargesTheFeeOnceAndRespectsRules() {
        UUID id = tournaments.create(admin, open(100, 2)).id();
        UUID player = UUID.randomUUID();
        tournaments.register(id, player);
        tournaments.register(id, player);

        ArgumentCaptor<WalletTransactionRequest> debit = ArgumentCaptor.forClass(WalletTransactionRequest.class);
        verify(wallet, times(1)).debit(debit.capture());
        assertEquals(new WalletTransactionRequest(player, 100, "TOURNAMENT_ENTRY_FEE", id.toString(),
                "tournament-fee:" + id + ":" + player), debit.getValue());
        assertEquals(1, tournaments.get(id).entries().size());

        UUID poor = UUID.randomUUID();
        doThrow(CompetitionExceptions.insufficientBalance()).when(wallet).debit(any());
        assertError(CompetitionError.COMP_INSUFFICIENT_BALANCE, () -> tournaments.register(id, poor));
        assertEquals(1, tournaments.get(id).entries().size());

        reset(wallet);
        tournaments.register(id, UUID.randomUUID());
        assertError(CompetitionError.COMP_TOURNAMENT_FULL, () -> tournaments.register(id, UUID.randomUUID()));

        UUID elite = tournaments.create(admin, new com.msspoker.competitionservice.dto.request.CreateTournamentRequest(
                "Giải Cao thủ", TournamentType.ELITE, Instant.now().plusSeconds(3600), 0, null, 5000L, UUID.randomUUID())).id();
        assertError(CompetitionError.COMP_FORBIDDEN, () -> tournaments.register(elite, player));
        assertError(CompetitionError.COMP_NOT_FOUND, () -> tournaments.register(UUID.randomUUID(), player));
    }

    @Test
    void bracketRunsFromQualifiersToFinal() {
        UUID id = tournaments.create(admin, open(50, null)).id();
        List<UUID> players = register(id, 18);

        TournamentResponse started = tournaments.start(id);
        assertEquals(TournamentStatus.QUALIFYING, started.status());
        assertEquals(List.of(6, 6, 6), started.tables().stream().map(t -> t.seats().size()).toList());
        assertTrue(started.tables().stream().allMatch(t -> t.round() == TournamentRound.QUALIFIER
                && t.status() == TableStatus.PLAYING && t.tableId() != null));
        assertEquals(new HashSet<>(players), new HashSet<>(started.tables().stream()
                .flatMap(t -> t.seats().stream()).map(s -> s.accountId()).toList()));

        ArgumentCaptor<CreateTableRequest> sent = ArgumentCaptor.forClass(CreateTableRequest.class);
        verify(game, times(3)).createTable(sent.capture());
        assertTrue(sent.getAllValues().stream().allMatch(r -> r.mode() == GameMode.TOURNAMENT && r.entryFee() == 50
                && r.settings() == null));
        assertEquals(started.tables().stream().map(t -> t.id()).toList(),
                sent.getAllValues().stream().map(CreateTableRequest::sourceId).toList());
        assertEquals(3, outbox.count());
        assertError(CompetitionError.COMP_CONFLICT, () -> tournaments.start(id));
        assertError(CompetitionError.COMP_CONFLICT, () -> tournaments.register(id, UUID.randomUUID()));

        List<TournamentTableEntity> qualifiers = tables.findByTournamentIdAndRoundOrderByTableIndexAsc(id, TournamentRound.QUALIFIER);
        finish(qualifiers.get(0));
        finish(qualifiers.get(0));
        finish(qualifiers.get(1));
        assertEquals(TournamentStatus.QUALIFYING, tournaments.get(id).status());
        List<TournamentSeatEntity> lastRoster = roster(qualifiers.get(2));
        var wrongPlayers = finished(qualifiers.get(2), lastRoster.subList(0, 5));
        assertThrows(IllegalArgumentException.class, () -> tournaments.matchFinished(wrongPlayers));
        finish(qualifiers.get(2));

        TournamentResponse finalRound = tournaments.get(id);
        assertEquals(TournamentStatus.FINAL, finalRound.status());
        var finalTable = finalRound.tables().getFirst();
        assertEquals(TournamentRound.FINAL, finalTable.round());
        assertEquals(TableStatus.PENDING, finalTable.status());
        List<UUID> expectedFinalists = qualifiers.stream().flatMap(q -> roster(q).subList(0, 2).stream())
                .map(TournamentSeatEntity::getAccountId).toList();
        assertEquals(expectedFinalists, finalTable.seats().stream().map(s -> s.accountId()).toList());
        assertEquals(12, finalRound.entries().stream().filter(e -> e.status() == EntryStatus.ELIMINATED).count());

        launcher.launchPending();
        TournamentTableEntity finalEntity = tables.findByTournamentIdAndRoundOrderByTableIndexAsc(id, TournamentRound.FINAL).getFirst();
        assertEquals(TableStatus.PLAYING, finalEntity.getStatus());
        finish(finalEntity);

        TournamentResponse done = tournaments.get(id);
        assertEquals(TournamentStatus.FINISHED, done.status());
        assertEquals(List.of(1, 2, 3, 4, 5, 6), expectedFinalists.stream().map(account -> done.entries().stream()
                .filter(e -> e.accountId().equals(account)).findFirst().orElseThrow().finalPlace()).toList());

        // Pool of 18 fees of 50 split 50/30/20 over the podium; no skin was configured.
        assertEquals(900, done.prizePool());
        assertEquals(List.of(450L, 270L, 180L), expectedFinalists.subList(0, 3).stream().map(account -> credits.findAll()
                .stream().filter(c -> c.getAccountId().equals(account)).findFirst().orElseThrow().getAmount()).toList());
        assertTrue(credits.findAll().stream().allMatch(c -> c.getReason().equals("TOURNAMENT_PRIZE")
                && c.getIdempotencyKey().equals("tournament-prize:" + id + ":" + c.getAccountId())));
        assertEquals(3, credits.count());
        assertEquals(0, itemGrants.count());
    }

    @Test
    void smallFieldPlaysOneFinalTable() {
        UUID id = tournaments.create(admin, open(0, null)).id();
        register(id, 5);
        TournamentResponse started = tournaments.start(id);

        assertEquals(TournamentStatus.FINAL, started.status());
        assertEquals(1, started.tables().size());
        assertEquals(TournamentRound.FINAL, started.tables().getFirst().round());
        verify(wallet, never()).debit(any());
    }

    @Test
    void tooFewPlayersCannotStartAndCancellationRefundsOnce() {
        UUID id = tournaments.create(admin, open(100, null)).id();
        List<UUID> players = register(id, 3);
        assertError(CompetitionError.COMP_CONFLICT, () -> tournaments.start(id));

        assertEquals(TournamentStatus.CANCELLED, tournaments.cancel(id).status());
        assertEquals(3, credits.count());
        assertTrue(credits.findAll().stream().allMatch(c -> c.getReason().equals("TOURNAMENT_REFUND")
                && c.getIdempotencyKey().equals("tournament-refund:" + id + ":" + c.getAccountId()) && c.getAmount() == 100));
        assertEquals(new HashSet<>(players), new HashSet<>(credits.findAll().stream().map(WalletCreditEntity::getAccountId).toList()));

        dispatcher.dispatchPending();
        dispatcher.dispatchPending();
        verify(wallet, times(3)).credit(any());
        assertTrue(credits.findAll().stream().allMatch(c -> c.getCreditedAt() != null));
        assertError(CompetitionError.COMP_CONFLICT, () -> tournaments.cancel(id));
    }

    @Test
    void failedLaunchStaysPendingUntilGameAcceptsTheSameSource() {
        UUID id = tournaments.create(admin, open(0, null)).id();
        register(id, 4);
        when(game.createTable(any())).thenThrow(CompetitionExceptions.gameUnavailable());

        TournamentResponse started = tournaments.start(id);
        assertEquals(TableStatus.PENDING, started.tables().getFirst().status());

        reset(game);
        when(game.createTable(any())).thenReturn(new TableCreatedResponse(UUID.randomUUID(), UUID.randomUUID()));
        launcher.launchPending();
        assertEquals(TableStatus.PLAYING, tournaments.get(id).tables().getFirst().status());
        ArgumentCaptor<CreateTableRequest> sent = ArgumentCaptor.forClass(CreateTableRequest.class);
        verify(game).createTable(sent.capture());
        assertEquals(started.tables().getFirst().id(), sent.getValue().sourceId());
        assertEquals("TOURNAMENT_TABLE_READY", json.readTree(outbox.findAll().getFirst().getBody())
                .get("payload").get("type").asString());
    }

    private List<UUID> register(UUID tournamentId, int count) {
        List<UUID> players = IntStream.range(0, count).mapToObj(i -> UUID.randomUUID()).toList();
        players.forEach(player -> tournaments.register(tournamentId, player));
        return players;
    }

    private List<TournamentSeatEntity> roster(TournamentTableEntity table) {
        return seats.findByTournamentTableIdOrderBySeatIndex(table.getId());
    }

    private void finish(TournamentTableEntity table) {
        tournaments.matchFinished(finished(tables.findById(table.getId()).orElseThrow(), roster(table)));
    }

    private void assertError(CompetitionError code, Executable action) {
        CompetitionApiException ex = assertThrows(CompetitionApiException.class, action);
        assertEquals(code, ex.getCode());
    }
}
