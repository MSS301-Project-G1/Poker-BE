package com.msspoker.gameservice.service;

import com.msspoker.gameservice.api.GameDtos;
import com.msspoker.gameservice.config.GameIds;
import com.msspoker.gameservice.engine.GameMode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
@Profile("dev")
@RequiredArgsConstructor
public class DevTableService {
    private final TableService tables;
    private final TableRegistry registry;

    public record DevTable(UUID tableId, UUID matchId, List<UUID> humanIds, List<UUID> botIds) {
    }

    public DevTable create(int humans, int bots) {
        List<UUID> playerIds = IntStream.range(0, humans + bots).mapToObj(i -> GameIds.next()).toList();
        var created = tables.create(new GameDtos.CreateTable(GameMode.NORMAL, GameIds.next(), playerIds, null, 0));
        ManagedTable managed = registry.require(created.tableId());
        managed.getLock().lock();
        try {
            List<UUID> botIds = playerIds.subList(humans, playerIds.size());
            managed.getBots().addAll(botIds);
            return new DevTable(created.tableId(), created.matchId(), playerIds.subList(0, humans), botIds);
        } finally {
            managed.getLock().unlock();
        }
    }
}
