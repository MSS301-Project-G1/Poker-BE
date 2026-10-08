package com.msspoker.gameservice.service.impl;

import com.msspoker.gameservice.dto.request.CreateTableRequest;
import com.msspoker.gameservice.dto.response.TableCreatedResponse;
import com.msspoker.gameservice.entity.MatchEntity;
import com.msspoker.gameservice.mapper.GameMapper;
import com.msspoker.gameservice.model.game.ManagedTable;
import com.msspoker.gameservice.model.game.PokerTable;
import com.msspoker.gameservice.service.MatchPersistenceService;
import com.msspoker.gameservice.service.SettingsService;
import com.msspoker.gameservice.service.TableRegistry;
import com.msspoker.gameservice.service.TableService;
import com.msspoker.gameservice.service.poker.HandEvaluator;
import com.msspoker.gameservice.service.poker.PotDistributor;
import com.msspoker.gameservice.util.GameIds;
import com.msspoker.gameservice.validator.CreateTableValidator;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TableServiceImpl implements TableService {
    private final SettingsService settings;
    private final MatchPersistenceService persistence;
    private final TableRegistry registry;
    private final GameMapper mapper;
    private final List<CreateTableValidator> validators;
    private final HandEvaluator handEvaluator;
    private final PotDistributor potDistributor;
    private final SecureRandom random = new SecureRandom();

    /** Creation gate prevents overlapping rosters; game actions use separate table locks. */
    @Override
    public synchronized TableCreatedResponse create(CreateTableRequest request) {
        Optional<MatchEntity> existing = persistence.existing(request);
        if (existing.isPresent()) {
            return mapper.created(existing.get());
        }
        validators.forEach(validator -> validator.validate(request));
        PokerTable engine = new PokerTable(GameIds.next(), GameIds.next(), request.playerIds(),
                settings.resolve(request), random, handEvaluator, potDistributor);
        ManagedTable managed = new ManagedTable(engine, persistence.started(request, engine));
        managed.setDeadline(Instant.now().plusSeconds(engine.getRules().turnTimeSeconds()));
        registry.register(managed);
        return mapper.created(engine);
    }

    @Override
    public Optional<TableCreatedResponse> active(UUID accountId) {
        return registry.activeTable(accountId).map(id -> mapper.created(registry.require(id).getEngine()));
    }
}
