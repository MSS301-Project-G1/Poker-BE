package com.msspoker.gameservice.service;

import com.msspoker.gameservice.model.game.ManagedTable;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface TableRegistry {
    Optional<UUID> activeTable(UUID accountId);

    ManagedTable require(UUID tableId);

    void register(ManagedTable table);

    void releasePlayers(ManagedTable table);

    Collection<ManagedTable> all();

    void remove(UUID tableId);
}
