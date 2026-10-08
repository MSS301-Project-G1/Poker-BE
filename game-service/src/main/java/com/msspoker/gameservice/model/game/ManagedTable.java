package com.msspoker.gameservice.model.game;

import com.msspoker.gameservice.entity.MatchEntity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

@Getter
@RequiredArgsConstructor
public class ManagedTable {
    private final PokerTable engine;
    private final MatchEntity match;
    private final ReentrantLock lock = new ReentrantLock();
    private final Set<UUID> bots = java.util.concurrent.ConcurrentHashMap.newKeySet();
    @Setter private Instant deadline;
    @Setter private Instant finishedAt;
    @Setter private boolean completionPersisted;
}
