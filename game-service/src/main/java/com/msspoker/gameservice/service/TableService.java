package com.msspoker.gameservice.service;

import com.msspoker.gameservice.api.GameDtos;
import com.msspoker.gameservice.config.GameIds;
import com.msspoker.gameservice.engine.PokerTable;
import com.msspoker.gameservice.persistence.MatchEntity;
import com.msspoker.gameservice.validation.CreateTableValidator;
import com.msspoker.gameservice.mapper.GameMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TableService {
    private final SettingsService settings;
    private final MatchPersistenceService persistence;
    private final TableRegistry registry;
    private final GameMapper mapper;
    private final List<CreateTableValidator> validators;
    private final SecureRandom random = new SecureRandom();

    /** Creation gate prevents overlapping rosters; game actions use separate table locks. */
    public synchronized GameDtos.TableCreated create(GameDtos.CreateTable request) {
        Optional<MatchEntity> existing = persistence.existing(request);
        if (existing.isPresent()) {
            return mapper.created(existing.get());
        }
        validators.forEach(validator -> validator.validate(request));
        PokerTable engine = new PokerTable(GameIds.next(), GameIds.next(), request.playerIds(), settings.resolve(request), random);
        ManagedTable managed = new ManagedTable(engine, persistence.started(request, engine));
        managed.setDeadline(Instant.now().plusSeconds(engine.getRules().turnTimeSeconds()));
        registry.register(managed);
        return mapper.created(engine);
    }

    public Optional<GameDtos.TableCreated> active(UUID accountId) {
        return registry.activeTable(accountId).map(id -> mapper.created(registry.require(id).getEngine()));
    }
}
