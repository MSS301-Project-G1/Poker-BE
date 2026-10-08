package com.msspoker.gameservice.service;

import com.msspoker.gameservice.config.GameProperties;
import com.msspoker.gameservice.dto.request.CreateTableRequest;
import com.msspoker.gameservice.dto.request.UpdateMatchSettingsRequest;
import com.msspoker.gameservice.dto.response.MatchSettingsResponse;
import com.msspoker.gameservice.entity.MatchSettingsEntity;
import com.msspoker.gameservice.enums.GameMode;
import com.msspoker.gameservice.mapper.GameMapper;
import com.msspoker.gameservice.model.game.TableRules;
import com.msspoker.gameservice.repository.MatchSettingsRepository;

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
                mapper.defaults(properties, mode), mode)));
    }

    @Transactional
    public List<MatchSettingsResponse> list() {
        return Arrays.stream(GameMode.values()).map(this::defaults).map(mapper::settings).toList();
    }

    @Transactional
    public MatchSettingsResponse update(GameMode mode, UpdateMatchSettingsRequest request) {
        MatchSettingsEntity entity = defaults(mode);
        mapper.updateSettings(request, entity);
        return mapper.settings(repository.save(entity));
    }

    public TableRules resolve(CreateTableRequest request) {
        TableRules rules = mapper.rules(defaults(request.mode()));
        if (request.settings() == null) return rules;
        return mapper.override(request.settings(), rules);
    }
}
