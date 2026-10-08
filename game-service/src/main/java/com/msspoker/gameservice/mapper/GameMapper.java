package com.msspoker.gameservice.mapper;

import com.msspoker.gameservice.api.GameDtos;
import com.msspoker.gameservice.config.GameIds;
import com.msspoker.gameservice.config.GameProperties;
import com.msspoker.gameservice.engine.*;
import com.msspoker.gameservice.events.GameEvents;
import com.msspoker.gameservice.persistence.*;
import org.mapstruct.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring", imports = {GameIds.class, MatchStatus.class, GameMode.class})
public interface GameMapper {
    TableRules rules(MatchSettingsEntity entity);
    GameDtos.MatchSettings settings(MatchSettingsEntity entity);
    GameDtos.TableCreated created(PokerTable table);

    @Mapping(target = "matchId", source = "id")
    GameDtos.TableCreated created(MatchEntity match);

    GameDtos.DevTable devTable(GameDtos.TableCreated created, List<UUID> humanIds, List<UUID> botIds);

    @Mapping(target = "maxPlayers", expression = "java(mode == GameMode.TOURNAMENT ? properties.getTournamentMaxPlayers() : properties.getMaxPlayers())")
    TableRules defaults(GameProperties properties, @Context GameMode mode);

    @Mapping(target = "smallBlind", source = "settings.smallBlind")
    @Mapping(target = "bigBlind", source = "settings.bigBlind")
    @Mapping(target = "startingChips", source = "settings.startingChips")
    @Mapping(target = "turnTimeSeconds", source = "settings.turnTimeSeconds")
    TableRules override(GameDtos.Settings settings, TableRules base);

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
    void updateSettings(GameDtos.UpdateSettings request, @MappingTarget MatchSettingsEntity entity);

    @Mapping(target = "id", source = "matchId")
    @Mapping(target = "status", expression = "java(MatchStatus.RUNNING)")
    @Mapping(target = "finishedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    MatchEntity match(GameDtos.CreateTable request, UUID tableId, UUID matchId, Instant startedAt, String requestFingerprint);

    @Mapping(target = "id", expression = "java(GameIds.next())")
    @Mapping(target = "finalChips", source = "seat.chips")
    @Mapping(target = "deleted", ignore = true)
    MatchPlayerEntity player(Seat seat, UUID matchId);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "matchId", ignore = true)
    @Mapping(target = "finalChips", source = "chips")
    @Mapping(target = "deleted", ignore = true)
    void finishPlayer(Seat seat, @MappingTarget MatchPlayerEntity entity);

    GameDtos.Placement placement(MatchPlayerEntity player);

    @Mapping(target = "matchId", source = "match.id")
    GameEvents.Started started(MatchEntity match, List<UUID> playerIds);

    @Mapping(target = "matchId", source = "match.id")
    GameEvents.Finished finished(MatchEntity match, List<GameDtos.Placement> placements);

    GameDtos.MatchView view(MatchEntity match, List<GameDtos.Placement> placements);
}
