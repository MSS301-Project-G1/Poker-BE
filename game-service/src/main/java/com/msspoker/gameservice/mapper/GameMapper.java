package com.msspoker.gameservice.mapper;

import com.msspoker.gameservice.config.GameProperties;
import com.msspoker.gameservice.dto.event.MatchFinishedEvent;
import com.msspoker.gameservice.dto.event.MatchStartedEvent;
import com.msspoker.gameservice.dto.request.CreateTableRequest;
import com.msspoker.gameservice.dto.request.TableSettingsRequest;
import com.msspoker.gameservice.dto.request.UpdateMatchSettingsRequest;
import com.msspoker.gameservice.dto.response.DevTableResponse;
import com.msspoker.gameservice.dto.response.MatchHistoryResponse;
import com.msspoker.gameservice.dto.response.MatchSettingsResponse;
import com.msspoker.gameservice.dto.response.PlacementResponse;
import com.msspoker.gameservice.dto.response.TableCreatedResponse;
import com.msspoker.gameservice.entity.MatchEntity;
import com.msspoker.gameservice.entity.MatchPlayerEntity;
import com.msspoker.gameservice.entity.MatchSettingsEntity;
import com.msspoker.gameservice.enums.GameMode;
import com.msspoker.gameservice.enums.MatchStatus;
import com.msspoker.gameservice.model.game.PokerTable;
import com.msspoker.gameservice.model.game.Seat;
import com.msspoker.gameservice.model.game.TableRules;
import com.msspoker.gameservice.util.GameIds;

import org.mapstruct.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring", imports = {GameIds.class, MatchStatus.class, GameMode.class})
public interface GameMapper {
    TableRules rules(MatchSettingsEntity entity);
    MatchSettingsResponse settings(MatchSettingsEntity entity);
    TableCreatedResponse created(PokerTable table);

    @Mapping(target = "matchId", source = "id")
    TableCreatedResponse created(MatchEntity match);

    DevTableResponse devTable(TableCreatedResponse created, List<UUID> humanIds, List<UUID> botIds);

    @Mapping(target = "maxPlayers", expression = "java(mode == GameMode.TOURNAMENT ? properties.getTournamentMaxPlayers() : properties.getMaxPlayers())")
    TableRules defaults(GameProperties properties, @Context GameMode mode);

    @Mapping(target = "smallBlind", source = "settings.smallBlind")
    @Mapping(target = "bigBlind", source = "settings.bigBlind")
    @Mapping(target = "startingChips", source = "settings.startingChips")
    @Mapping(target = "turnTimeSeconds", source = "settings.turnTimeSeconds")
    TableRules override(TableSettingsRequest settings, TableRules base);

    @Mapping(target = "id", expression = "java(GameIds.next())")
    @Mapping(target = "mode", source = "mode")
    @Mapping(target = "deleted", ignore = true)
    MatchSettingsEntity settingsEntity(TableRules rules, GameMode mode);

    @Mapping(target = "smallBlind", source = "request.settings.smallBlind")
    @Mapping(target = "bigBlind", source = "request.settings.bigBlind")
    @Mapping(target = "startingChips", source = "request.settings.startingChips")
    @Mapping(target = "turnTimeSeconds", source = "request.settings.turnTimeSeconds")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "mode", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateSettings(UpdateMatchSettingsRequest request, @MappingTarget MatchSettingsEntity entity);

    @Mapping(target = "id", source = "matchId")
    @Mapping(target = "status", expression = "java(MatchStatus.RUNNING)")
    @Mapping(target = "finishedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    MatchEntity match(CreateTableRequest request, UUID tableId, UUID matchId, Instant startedAt, String requestFingerprint);

    @Mapping(target = "id", expression = "java(GameIds.next())")
    @Mapping(target = "finalChips", source = "seat.chips")
    @Mapping(target = "deleted", ignore = true)
    MatchPlayerEntity player(Seat seat, UUID matchId);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "matchId", ignore = true)
    @Mapping(target = "finalChips", source = "chips")
    @Mapping(target = "deleted", ignore = true)
    void finishPlayer(Seat seat, @MappingTarget MatchPlayerEntity entity);

    PlacementResponse placement(MatchPlayerEntity player);

    @Mapping(target = "matchId", source = "match.id")
    MatchStartedEvent started(MatchEntity match, List<UUID> playerIds);

    @Mapping(target = "matchId", source = "match.id")
    MatchFinishedEvent finished(MatchEntity match, List<PlacementResponse> placements);

    MatchHistoryResponse view(MatchEntity match, List<PlacementResponse> placements);
}
