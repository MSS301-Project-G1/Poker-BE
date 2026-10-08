package com.msspoker.gameservice.service;

import com.msspoker.gameservice.dto.request.CreateTableRequest;
import com.msspoker.gameservice.dto.response.DevTableResponse;
import com.msspoker.gameservice.enums.GameMode;
import com.msspoker.gameservice.mapper.GameMapper;
import com.msspoker.gameservice.model.game.ManagedTable;
import com.msspoker.gameservice.util.GameIds;

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
    private final GameMapper mapper;

    public DevTableResponse create(int humans, int bots) {
        List<UUID> playerIds = IntStream.range(0, humans + bots).mapToObj(i -> GameIds.next()).toList();
        var created = tables.create(new CreateTableRequest(GameMode.NORMAL, GameIds.next(), playerIds, null, 0));
        ManagedTable managed = registry.require(created.tableId());
        managed.getLock().lock();
        try {
            List<UUID> botIds = playerIds.subList(humans, playerIds.size());
            managed.getBots().addAll(botIds);
            return mapper.devTable(created, playerIds.subList(0, humans), botIds);
        } finally {
            managed.getLock().unlock();
        }
    }
}
