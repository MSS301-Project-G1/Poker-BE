package com.msspoker.gameservice.service.impl;

import com.msspoker.gameservice.config.GameProperties;
import com.msspoker.gameservice.dto.request.ActionRequest;
import com.msspoker.gameservice.dto.response.GameSnapshotResponse;
import com.msspoker.gameservice.enums.ActionType;
import com.msspoker.gameservice.enums.Street;
import com.msspoker.gameservice.mapper.SnapshotMapper;
import com.msspoker.gameservice.model.game.ManagedTable;
import com.msspoker.gameservice.model.game.PokerTable;
import com.msspoker.gameservice.model.game.Seat;
import com.msspoker.gameservice.service.GameplayService;
import com.msspoker.gameservice.service.MatchPersistenceService;
import com.msspoker.gameservice.service.TableRegistry;
import com.msspoker.gameservice.validator.TableMembershipValidator;
import com.msspoker.gameservice.websocket.SnapshotPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameplayServiceImpl implements GameplayService {
    private static final long BOT_THINK_MILLIS = 700;
    private final TableRegistry registry;
    private final TableMembershipValidator membership;
    private final SnapshotMapper mapper;
    private final SnapshotPublisher publisher;
    private final MatchPersistenceService persistence;
    private final GameProperties properties;

    @Override
    public GameSnapshotResponse snapshot(UUID tableId, UUID accountId) {
        ManagedTable table = registry.require(tableId);
        membership.validate(table, accountId);
        table.getLock().lock();
        try {
            return mapper.snapshot(table, accountId);
        } finally {
            table.getLock().unlock();
        }
    }

    @Override
    public void sync(UUID tableId, UUID accountId) {
        ManagedTable table = registry.require(tableId);
        membership.validate(table, accountId);
        table.getLock().lock();
        try {
            publisher.onePlayer(table, accountId);
        } finally {
            table.getLock().unlock();
        }
    }

    @Override
    public void action(UUID tableId, UUID accountId, ActionRequest request) {
        ManagedTable table = registry.require(tableId);
        membership.validate(table, accountId);
        table.getLock().lock();
        try {
            PokerTable engine = table.getEngine();
            long sequence = request.actionSequence() == null ? engine.getSequence() : request.actionSequence();
            if (request.type() == ActionType.RAISE) engine.raise(accountId, request.amount(), sequence);
            else engine.act(accountId, request.type(), sequence);
            afterTransition(table);
            publisher.allPlayers(table);
        } finally {
            table.getLock().unlock();
        }
    }

    @Scheduled(fixedRateString = "${game.tick-millis:100}")
    @Override
    public void tick() {
        Instant now = Instant.now();
        for (ManagedTable table : registry.all()) {
            if (!table.getLock().tryLock()) continue;
            try {
                PokerTable engine = table.getEngine();
                if (engine.getStreet() == Street.FINISHED) {
                    complete(table);
                    if (table.isCompletionPersisted() && table.getFinishedAt() != null
                            && now.isAfter(table.getFinishedAt().plusSeconds(properties.getTableRetentionSeconds()))) {
                        registry.remove(engine.getTableId());
                    }
                } else if (engine.getStreet() == Street.HAND_FINISHED) {
                    if (!now.isBefore(table.getDeadline())) {
                        engine.startHand();
                        afterTransition(table);
                        publisher.allPlayers(table);
                    }
                } else {
                    Seat actor = engine.getSeats().get(engine.getActorSeat());
                    Instant botTime = table.getDeadline().minusSeconds(engine.getRules().turnTimeSeconds())
                            .plusMillis(BOT_THINK_MILLIS);
                    if (table.getBots().contains(actor.getAccountId()) && !now.isBefore(botTime)) {
                        engine.act(actor.getAccountId(), botAction(engine, actor), engine.getSequence());
                        afterTransition(table);
                        publisher.allPlayers(table);
                    } else if (!now.isBefore(table.getDeadline())) {
                        long sequence = engine.getSequence();
                        if (engine.timeout(sequence, properties.getAfkTurns())) {
                            afterTransition(table);
                            publisher.allPlayers(table);
                        }
                    }
                }
            } catch (Exception ex) {
                log.error("Table tick failed for {}", table.getEngine().getTableId(), ex);
            } finally {
                table.getLock().unlock();
            }
        }
    }

    private ActionType botAction(PokerTable engine, Seat actor) {
        int decision = ThreadLocalRandom.current().nextInt(10);
        if (decision == 0 && actor.getStreetBet() < engine.getCurrentBet()) return ActionType.FOLD;
        if (decision < 3 && engine.canRaise(actor)) return ActionType.ALL_IN;
        return actor.getStreetBet() >= engine.getCurrentBet() ? ActionType.CHECK : ActionType.CALL;
    }

    private void afterTransition(ManagedTable table) {
        PokerTable engine = table.getEngine();
        table.setDeadline(engine.getStreet() == Street.HAND_FINISHED
                ? Instant.now().plusMillis(properties.getHandPauseMillis())
                : Instant.now().plusSeconds(engine.getRules().turnTimeSeconds()));
        if (engine.getStreet() == Street.FINISHED) complete(table);
    }

    private void complete(ManagedTable table) {
        if (table.isCompletionPersisted()) return;
        try {
            persistence.finished(table);
            table.setCompletionPersisted(true);
            table.setFinishedAt(Instant.now());
            registry.releasePlayers(table);
        } catch (Exception ex) {
            log.error("Match completion persistence failed; retrying on next tick for {}", table.getEngine().getMatchId(), ex);
        }
    }
}
