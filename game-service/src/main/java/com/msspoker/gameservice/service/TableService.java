package com.msspoker.gameservice.service;

import com.msspoker.gameservice.api.GameDtos;
import com.msspoker.gameservice.config.GameIds;
import com.msspoker.gameservice.config.GameProperties;
import com.msspoker.gameservice.engine.PokerTable;
import com.msspoker.gameservice.persistence.MatchEntity;
import com.msspoker.gameservice.validation.CreateTableValidator;
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
    private final GameProperties properties;
    private final List<CreateTableValidator> validators;
    private final SecureRandom random = new SecureRandom();

    /** Creation gate prevents overlapping rosters; game actions use separate table locks. */
    public synchronized GameDtos.TableCreated create(GameDtos.CreateTable request) {
        Optional<MatchEntity> existing = persistence.existing(request);
        if (existing.isPresent()) {
            MatchEntity match = existing.get();
            return new GameDtos.TableCreated(match.getTableId(), match.getId());
        }
        validators.forEach(validator -> validator.validate(request));
        PokerTable engine = new PokerTable(GameIds.next(), GameIds.next(), request.playerIds(), settings.resolve(request), random.nextLong());
        ManagedTable managed = new ManagedTable(engine, persistence.started(request, engine));
        managed.setDeadline(Instant.now().plusSeconds(engine.getRules().turnTimeSeconds()));
        registry.register(managed);
        return new GameDtos.TableCreated(engine.getTableId(), engine.getMatchId());
    }

    public Optional<GameDtos.TableCreated> active(UUID accountId) {
        return registry.activeTable(accountId).map(id -> {
            ManagedTable table = registry.require(id);
            return new GameDtos.TableCreated(id, table.getEngine().getMatchId());
        });
    }
}
