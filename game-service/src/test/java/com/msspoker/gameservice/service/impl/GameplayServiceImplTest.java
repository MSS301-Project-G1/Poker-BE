package com.msspoker.gameservice.service.impl;

import com.msspoker.gameservice.config.GameProperties;
import com.msspoker.gameservice.dto.request.ActionRequest;
import com.msspoker.gameservice.entity.MatchEntity;
import com.msspoker.gameservice.enums.ActionType;
import com.msspoker.gameservice.enums.Street;
import com.msspoker.gameservice.mapper.SnapshotMapper;
import com.msspoker.gameservice.model.game.ManagedTable;
import com.msspoker.gameservice.model.game.PokerTable;
import com.msspoker.gameservice.model.game.TableRules;
import com.msspoker.gameservice.service.MatchPersistenceService;
import com.msspoker.gameservice.service.TableRegistry;
import com.msspoker.gameservice.service.impl.poker.HandEvaluatorImpl;
import com.msspoker.gameservice.service.impl.poker.PotDistributorImpl;
import com.msspoker.gameservice.validator.TableMembershipValidator;
import com.msspoker.gameservice.websocket.SnapshotPublisher;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GameplayServiceImplTest {
    private static final TableRules RULES = new TableRules(10, 20, 1000, 20, 2, 6);

    private final TableRegistry registry = mock(TableRegistry.class);
    private final GameProperties properties = new GameProperties();
    private final GameplayServiceImpl gameplay = new GameplayServiceImpl(registry, new TableMembershipValidator(),
            mock(SnapshotMapper.class), mock(SnapshotPublisher.class), mock(MatchPersistenceService.class), properties);

    @Test
    void showdownPausesLongerThanAFoldSoClientsCanRevealTheHand() {
        ManagedTable showdown = table();
        while (showdown.getEngine().getStreet() != Street.HAND_FINISHED) {
            PokerTable engine = showdown.getEngine();
            act(showdown, engine.getSeats().get(engine.getActorSeat()).getStreetBet() < engine.getCurrentBet()
                    ? ActionType.CALL : ActionType.CHECK);
        }
        assertFalse(showdown.getEngine().getShowdownHands().isEmpty());
        assertPause(showdown, properties.getShowdownPauseMillis());

        ManagedTable fold = table();
        act(fold, ActionType.FOLD);
        assertEquals(Street.HAND_FINISHED, fold.getEngine().getStreet());
        assertPause(fold, properties.getHandPauseMillis());
    }

    private ManagedTable table() {
        PokerTable engine = new PokerTable(UUID.randomUUID(), UUID.randomUUID(), List.of(UUID.randomUUID(), UUID.randomUUID()),
                RULES, new Random(7), new HandEvaluatorImpl(), new PotDistributorImpl());
        ManagedTable table = new ManagedTable(engine, new MatchEntity());
        when(registry.require(engine.getTableId())).thenReturn(table);
        return table;
    }

    private void act(ManagedTable table, ActionType type) {
        PokerTable engine = table.getEngine();
        UUID actor = engine.getSeats().get(engine.getActorSeat()).getAccountId();
        gameplay.action(engine.getTableId(), actor, new ActionRequest(type, null, engine.getSequence()));
    }

    private static void assertPause(ManagedTable table, long expectedMillis) {
        long remaining = Duration.between(Instant.now(), table.getDeadline()).toMillis();
        assertTrue(remaining > expectedMillis - 1000 && remaining <= expectedMillis, "pause was " + remaining + " ms");
    }
}
