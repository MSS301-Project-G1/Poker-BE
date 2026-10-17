package com.msspoker.gameservice.service;

import com.msspoker.gameservice.dto.request.CreateTableRequest;
import com.msspoker.gameservice.dto.request.UpdateMatchSettingsRequest;
import com.msspoker.gameservice.dto.response.MatchSettingsResponse;
import com.msspoker.gameservice.entity.MatchSettingsEntity;
import com.msspoker.gameservice.enums.GameMode;
import com.msspoker.gameservice.model.game.TableRules;

import java.util.List;

public interface SettingsService {
    MatchSettingsEntity defaults(GameMode mode);

    List<MatchSettingsResponse> list();

    MatchSettingsResponse update(GameMode mode, UpdateMatchSettingsRequest request);

    TableRules resolve(CreateTableRequest request);
}
