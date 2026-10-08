package com.msspoker.gameservice.service;

import com.msspoker.gameservice.api.GameDtos;
import com.msspoker.gameservice.config.GameProperties;
import com.msspoker.gameservice.engine.*;
import com.msspoker.gameservice.mapper.GameMapper;
import com.msspoker.gameservice.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SettingsService {
    private final MatchSettingsRepository repository;
    private final GameMapper mapper;
    private final GameProperties properties;

    @Transactional
    public MatchSettingsEntity defaults(GameMode mode) {
        return repository.findByMode(mode).orElseGet(() -> repository.save(mapper.settingsEntity(
                new TableRules(properties.getSmallBlind(), properties.getBigBlind(), properties.getStartingChips(),
                        properties.getTurnTimeSeconds(), properties.getMinPlayers(),
                        mode == GameMode.TOURNAMENT ? properties.getTournamentMaxPlayers() : properties.getMaxPlayers()), mode)));
    }

    @Transactional
    public List<GameDtos.MatchSettings> list() {
        return Arrays.stream(GameMode.values()).map(this::defaults).map(mapper::settings).toList();
    }

    @Transactional
    public GameDtos.MatchSettings update(GameMode mode, GameDtos.UpdateSettings request) {
        MatchSettingsEntity entity = defaults(mode);
        mapper.updateSettings(request, entity);
        return mapper.settings(repository.save(entity));
    }

    public TableRules resolve(GameDtos.CreateTable request) {
        TableRules rules = mapper.rules(defaults(request.mode()));
        if (request.settings() == null) return rules;
        GameDtos.Settings override = request.settings();
        return new TableRules(override.smallBlind(), override.bigBlind(), override.startingChips(),
                override.turnTimeSeconds(), rules.minPlayers(), rules.maxPlayers());
    }
}
