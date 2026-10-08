package com.msspoker.gameservice.service;

import com.msspoker.gameservice.exception.GameExceptions;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TableRegistry {
    private final Map<UUID, ManagedTable> tables = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> activePlayers = new ConcurrentHashMap<>();

    public Optional<UUID> activeTable(UUID accountId) {
        return Optional.ofNullable(activePlayers.get(accountId));
    }

    public ManagedTable require(UUID tableId) {
        ManagedTable table = tables.get(tableId);
        if (table == null) throw GameExceptions.tableNotFound();
        return table;
    }

    public void register(ManagedTable table) {
        UUID id = table.getEngine().getTableId();
        tables.put(id, table);
        table.getEngine().getSeats().forEach(s -> activePlayers.put(s.getAccountId(), id));
    }

    public void releasePlayers(ManagedTable table) {
        UUID id = table.getEngine().getTableId();
        table.getEngine().getSeats().forEach(s -> activePlayers.remove(s.getAccountId(), id));
    }

    public Collection<ManagedTable> all() {
        return tables.values();
    }

    public void remove(UUID tableId) {
        tables.remove(tableId);
    }
}
