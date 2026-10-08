package com.msspoker.gameservice.service;

import com.msspoker.gameservice.api.GameDtos;
import com.msspoker.gameservice.engine.*;
import com.msspoker.gameservice.realtime.ActionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:poker-parallel;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class ManyTablesTest {
    private static final int BACKGROUND_CPU_WORKERS = 2;
    @Autowired TableService tables;
    @Autowired TableRegistry registry;
    @Autowired GameplayService gameplay;
    @Autowired MatchPersistenceService persistence;

    @Test
    void thirtyTwoTablesFinishIndependentlyWithConservedChipsAndPersistedPlacements() throws Exception {
        List<GameDtos.TableCreated> created = IntStream.range(0, 32).mapToObj(index -> tables.create(
                new GameDtos.CreateTable(GameMode.TOURNAMENT, UUID.randomUUID(),
                        IntStream.range(0, 2 + index % 7).mapToObj(i -> UUID.randomUUID()).toList(), null, 0))).toList();
        try (ExecutorService workers = Executors.newFixedThreadPool(BACKGROUND_CPU_WORKERS)) {
            var tasks = created.stream().map(table -> workers.submit(() -> play(table))).toList();
            for (Future<?> task : tasks) task.get(30, TimeUnit.SECONDS);
        }
        for (var table : created) {
            var managed = registry.require(table.tableId());
            assertTrue(managed.isCompletionPersisted());
            UUID player = managed.getEngine().getSeats().getFirst().getAccountId();
            assertTrue(tables.active(player).isEmpty());
            assertEquals("FINISHED", persistence.result(table.tableId(), player).status());
            managed.getEngine().assertConserved();
        }
    }

    private void play(GameDtos.TableCreated created) {
        ManagedTable table = registry.require(created.tableId());
        int guard = 0;
        while (table.getEngine().getStreet() != Street.FINISHED) {
            assertTrue(++guard < 10000);
            PokerTable engine = table.getEngine();
            if (engine.getStreet() == Street.HAND_FINISHED) {
                table.getLock().lock();
                try { engine.startHand(); } finally { table.getLock().unlock(); }
            } else {
                Seat actor = engine.getSeats().get(engine.getActorSeat());
                ActionType type = engine.canRaise(actor) ? ActionType.ALL_IN
                        : actor.getStreetBet() < engine.getCurrentBet() ? ActionType.CALL : ActionType.CHECK;
                gameplay.action(created.tableId(), actor.getAccountId(), new ActionRequest(type, null, engine.getSequence()));
            }
        }
    }
}
