package com.msspoker.competitionservice.mapper;

import com.msspoker.competitionservice.dto.request.CreateTournamentRequest;
import com.msspoker.competitionservice.dto.request.ShopGrantRequest;
import com.msspoker.competitionservice.dto.request.WalletTransactionRequest;
import com.msspoker.competitionservice.dto.response.TournamentEntryResponse;
import com.msspoker.competitionservice.dto.response.TournamentResponse;
import com.msspoker.competitionservice.dto.response.TournamentSeatResponse;
import com.msspoker.competitionservice.dto.response.TournamentSummaryResponse;
import com.msspoker.competitionservice.dto.response.TournamentTableResponse;
import com.msspoker.competitionservice.entity.ItemGrantEntity;
import com.msspoker.competitionservice.entity.TournamentEntity;
import com.msspoker.competitionservice.entity.TournamentEntryEntity;
import com.msspoker.competitionservice.entity.TournamentSeatEntity;
import com.msspoker.competitionservice.entity.TournamentTableEntity;
import com.msspoker.competitionservice.entity.WalletCreditEntity;
import com.msspoker.competitionservice.enums.EntryStatus;
import com.msspoker.competitionservice.enums.TableStatus;
import com.msspoker.competitionservice.enums.TournamentRound;
import com.msspoker.competitionservice.enums.TournamentStatus;
import com.msspoker.competitionservice.util.CompetitionIds;

import org.mapstruct.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring", imports = {CompetitionIds.class, TournamentStatus.class, TableStatus.class})
public interface TournamentMapper {
    @Mapping(target = "id", expression = "java(CompetitionIds.next())")
    @Mapping(target = "status", expression = "java(TournamentStatus.SCHEDULED)")
    @Mapping(target = "slots", source = "slots")
    @Mapping(target = "invitesSentAt", ignore = true)
    @Mapping(target = "replacementsSentAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    TournamentEntity newTournament(CreateTournamentRequest request, int slots, UUID createdBy, Instant createdAt);

    @Mapping(target = "id", expression = "java(CompetitionIds.next())")
    @Mapping(target = "finalPlace", ignore = true)
    TournamentEntryEntity newEntry(UUID tournamentId, UUID accountId, EntryStatus status, Instant registeredAt);

    @Mapping(target = "id", expression = "java(CompetitionIds.next())")
    @Mapping(target = "status", expression = "java(TableStatus.PENDING)")
    @Mapping(target = "tableId", ignore = true)
    @Mapping(target = "matchId", ignore = true)
    TournamentTableEntity newTable(UUID tournamentId, TournamentRound round, int tableIndex, Instant createdAt);

    @Mapping(target = "id", expression = "java(CompetitionIds.next())")
    @Mapping(target = "place", ignore = true)
    TournamentSeatEntity newSeat(UUID tournamentTableId, UUID accountId, int seatIndex);

    @Mapping(target = "id", expression = "java(CompetitionIds.next())")
    @Mapping(target = "creditedAt", ignore = true)
    @Mapping(target = "attempts", ignore = true)
    WalletCreditEntity newCredit(UUID accountId, long amount, String reason, String refId, String idempotencyKey,
                                 Instant createdAt);

    WalletTransactionRequest transaction(WalletCreditEntity credit);

    @Mapping(target = "id", expression = "java(CompetitionIds.next())")
    @Mapping(target = "grantedAt", ignore = true)
    @Mapping(target = "attempts", ignore = true)
    ItemGrantEntity newItemGrant(UUID accountId, UUID productId, String refId, String idempotencyKey, Instant createdAt);

    ShopGrantRequest shopGrant(ItemGrantEntity grant);

    TournamentSummaryResponse summary(TournamentEntity tournament, int entryCount, long prizePool);

    TournamentEntryResponse entry(TournamentEntryEntity entry, Instant confirmBy);

    TournamentSeatResponse seat(TournamentSeatEntity seat);

    TournamentTableResponse table(TournamentTableEntity table, List<TournamentSeatResponse> seats);

    TournamentResponse tournament(TournamentEntity tournament, long prizePool, List<Long> prizes,
                                  List<TournamentEntryResponse> entries, List<TournamentTableResponse> tables);
}
