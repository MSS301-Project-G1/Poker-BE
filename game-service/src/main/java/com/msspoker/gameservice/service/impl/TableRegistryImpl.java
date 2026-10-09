package com.msspoker.gameservice.service.impl;

import com.msspoker.gameservice.exception.GameExceptions;
import com.msspoker.gameservice.model.game.ManagedTable;
import com.msspoker.gameservice.service.TableRegistry;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TableRegistryImpl implements TableRegistry {
    private final Map<UUID, ManagedTable> tables = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> activePlayers = new ConcurrentHashMap<>();

    @Override
    public Optional<UUID> activeTable(UUID accountId) {
        return Optional.ofNullable(activePlayers.get(accountId));
    }

    @Override
    public ManagedTable require(UUID tableId) {
        ManagedTable table = tables.get(tableId);
        if (table == null) throw GameExceptions.tableNotFound();
        return table;
    }

    @Override
    public void register(ManagedTable table) {
        UUID id = table.getEngine().getTableId();
        tables.put(id, table);
        table.getEngine().getSeats().forEach(s -> activePlayers.put(s.getAccountId(), id));
    }

    @Override
    public void releasePlayers(ManagedTable table) {
        UUID id = table.getEngine().getTableId();
        table.getEngine().getSeats().forEach(s -> activePlayers.remove(s.getAccountId(), id));
    }

    @Override
    public Collection<ManagedTable> all() {
        return tables.values();
    }

    @Override
    public void remove(UUID tableId) {
        tables.remove(tableId);
    }
}
