package com.msspoker.gameservice.mapper;

import com.msspoker.gameservice.api.GameDtos;
import com.msspoker.gameservice.engine.*;
import com.msspoker.gameservice.realtime.GameSnapshot;
import com.msspoker.gameservice.service.ManagedTable;
import org.mapstruct.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring", imports = {Instant.class, List.class, Street.class})
public interface SnapshotMapper {
    @Mapping(target = "tableId", source = "managed.engine.tableId")
    @Mapping(target = "matchId", source = "managed.engine.matchId")
    @Mapping(target = "mode", source = "managed.match.mode")
    @Mapping(target = "street", source = "managed.engine.street")
    @Mapping(target = "handNumber", source = "managed.engine.handNumber")
    @Mapping(target = "actionSequence", source = "managed.engine.sequence")
    @Mapping(target = "dealerSeat", source = "managed.engine.dealerSeat")
    @Mapping(target = "smallBlindSeat", source = "managed.engine.smallBlindSeat")
    @Mapping(target = "bigBlindSeat", source = "managed.engine.bigBlindSeat")
    @Mapping(target = "actorSeat", source = "managed.engine.actorSeat")
    @Mapping(target = "board", expression = "java(List.copyOf(managed.getEngine().getBoard()))")
    @Mapping(target = "pot", expression = "java(managed.getEngine().pot())")
    @Mapping(target = "currentBet", source = "managed.engine.currentBet")
    @Mapping(target = "deadline", source = "managed.deadline")
    @Mapping(target = "serverTime", expression = "java(Instant.now())")
    @Mapping(target = "seats", expression = "java(players(managed, accountId))")
    @Mapping(target = "legalActions", expression = "java(legalActions(managed.getEngine(), accountId))")
    @Mapping(target = "callAmount", expression = "java(callAmount(managed.getEngine(), accountId))")
    @Mapping(target = "minRaiseTo", expression = "java(managed.getEngine().getCurrentBet() + managed.getEngine().getLastFullRaise())")
    @Mapping(target = "maxRaiseTo", expression = "java(ownSeat(managed.getEngine(), accountId).getChips() + ownSeat(managed.getEngine(), accountId).getStreetBet())")
    @Mapping(target = "placements", expression = "java(managed.getEngine().getStreet() == Street.FINISHED ? managed.getEngine().getSeats().stream().map(this::placement).toList() : List.of())")
    @Mapping(target = "distribution", source = "managed.engine.distribution")
    GameSnapshot snapshot(ManagedTable managed, UUID accountId);

    @Mapping(target = "holeCardCount", expression = "java(seat.getHoleCards().size())")
    @Mapping(target = "cards", expression = "java(seat.getAccountId().equals(accountId) || table.getShowdownHands().containsKey(seat.getSeatIndex()) ? List.copyOf(seat.getHoleCards()) : List.of())")
    @Mapping(target = "handRank", expression = "java(table.getShowdownHands().get(seat.getSeatIndex()))")
    GameSnapshot.Player player(Seat seat, @Context PokerTable table, @Context UUID accountId);

    @Mapping(target = "finalChips", source = "chips")
    GameDtos.Placement placement(Seat seat);

    default List<GameSnapshot.Player> players(ManagedTable table, UUID accountId) {
        return table.getEngine().getSeats().stream().map(s -> player(s, table.getEngine(), accountId)).toList();
    }

    default Seat ownSeat(PokerTable table, UUID accountId) {
        return table.getSeats().stream().filter(s -> s.getAccountId().equals(accountId)).findFirst().orElseThrow();
    }

    default List<ActionType> legalActions(PokerTable table, UUID accountId) {
        Seat own = ownSeat(table, accountId);
        if (table.getActorSeat() != own.getSeatIndex()) return List.of();
        List<ActionType> actions = new ArrayList<>();
        actions.add(ActionType.FOLD);
        actions.add(own.getStreetBet() >= table.getCurrentBet() ? ActionType.CHECK : ActionType.CALL);
        if (table.canRaise(own)) actions.add(ActionType.RAISE);
        if (table.canRaise(own) || own.getStreetBet() + own.getChips() <= table.getCurrentBet()) actions.add(ActionType.ALL_IN);
        return List.copyOf(actions);
    }

    default long callAmount(PokerTable table, UUID accountId) {
        Seat own = ownSeat(table, accountId);
        return Math.min(own.getChips(), Math.max(0, table.getCurrentBet() - own.getStreetBet()));
    }
}
