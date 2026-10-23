package com.msspoker.matchmakingservice.mapper;

import com.msspoker.matchmakingservice.dto.response.QueueStatusResponse;
import com.msspoker.matchmakingservice.entity.MatchProposalPlayerEntity;
import com.msspoker.matchmakingservice.model.QueueEntry;
import com.msspoker.matchmakingservice.model.InGameSession;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface QueueStatusMapper {
    @Mapping(target = "status", source = "status")
    @Mapping(target = "mode", source = "entry.mode")
    @Mapping(target = "joinedAt", source = "entry.joinedAt")
    @Mapping(target = "position", source = "position")
    @Mapping(target = "playerCount", source = "playerCount")
    @Mapping(target = "minPlayers", source = "minPlayers")
    @Mapping(target = "maxPlayers", source = "maxPlayers")
    @Mapping(target = "matchStartDeadline", source = "matchStartDeadline")
    @Mapping(target = "acceptDeadline", ignore = true)
    @Mapping(target = "proposalId", ignore = true)
    @Mapping(target = "tableId", ignore = true)
    @Mapping(target = "matchId", ignore = true)
    @Mapping(target = "playerIds", source = "playerIds")
    QueueStatusResponse waiting(
            QueueEntry entry,
            String status,
            Long position,
            Long playerCount,
            Integer minPlayers,
            Integer maxPlayers,
            Instant matchStartDeadline,
            List<UUID> playerIds
    );

    @Mapping(target = "status", constant = "MATCHING")
    @Mapping(target = "mode", source = "entry.mode")
    @Mapping(target = "joinedAt", source = "entry.joinedAt")
    @Mapping(target = "position", ignore = true)
    @Mapping(target = "playerCount", ignore = true)
    @Mapping(target = "minPlayers", ignore = true)
    @Mapping(target = "maxPlayers", ignore = true)
    @Mapping(target = "matchStartDeadline", ignore = true)
    @Mapping(target = "acceptDeadline", ignore = true)
    @Mapping(target = "proposalId", source = "proposalId")
    @Mapping(target = "tableId", ignore = true)
    @Mapping(target = "matchId", ignore = true)
    @Mapping(target = "playerIds", expression = "java(java.util.List.of(entry.accountId()))")
    QueueStatusResponse matching(QueueEntry entry, UUID proposalId);

    @Mapping(target = "status", source = "proposal.status")
    @Mapping(target = "mode", source = "proposal.mode")
    @Mapping(target = "joinedAt", source = "queueJoinedAt")
    @Mapping(target = "position", ignore = true)
    @Mapping(target = "playerCount", ignore = true)
    @Mapping(target = "minPlayers", ignore = true)
    @Mapping(target = "maxPlayers", ignore = true)
    @Mapping(target = "matchStartDeadline", ignore = true)
    @Mapping(target = "acceptDeadline", source = "proposal.acceptDeadline")
    @Mapping(target = "proposalId", source = "proposal.id")
    @Mapping(target = "tableId", source = "proposal.tableId")
    @Mapping(target = "matchId", source = "proposal.matchId")
    @Mapping(target = "playerIds", source = "proposal.players")
    QueueStatusResponse proposal(MatchProposalPlayerEntity player);

    @Mapping(target = "status", constant = "PROPOSED")
    @Mapping(target = "mode", source = "proposal.mode")
    @Mapping(target = "joinedAt", source = "queueJoinedAt")
    @Mapping(target = "position", ignore = true)
    @Mapping(target = "playerCount", ignore = true)
    @Mapping(target = "minPlayers", ignore = true)
    @Mapping(target = "maxPlayers", ignore = true)
    @Mapping(target = "matchStartDeadline", ignore = true)
    @Mapping(target = "acceptDeadline", source = "proposal.acceptDeadline")
    @Mapping(target = "proposalId", source = "proposal.id")
    @Mapping(target = "tableId", ignore = true)
    @Mapping(target = "matchId", ignore = true)
    @Mapping(target = "playerIds", source = "proposal.players")
    QueueStatusResponse rankProposal(MatchProposalPlayerEntity player);

    @Mapping(target = "status", constant = "READY")
    @Mapping(target = "mode", source = "mode")
    @Mapping(target = "joinedAt", ignore = true)
    @Mapping(target = "position", ignore = true)
    @Mapping(target = "playerCount", ignore = true)
    @Mapping(target = "minPlayers", ignore = true)
    @Mapping(target = "maxPlayers", ignore = true)
    @Mapping(target = "matchStartDeadline", ignore = true)
    @Mapping(target = "acceptDeadline", ignore = true)
    @Mapping(target = "proposalId", source = "proposalId")
    @Mapping(target = "tableId", source = "tableId")
    @Mapping(target = "matchId", source = "matchId")
    @Mapping(target = "playerIds", expression = "java(java.util.List.of())")
    QueueStatusResponse inGame(InGameSession session);

    default List<UUID> playerIds(List<MatchProposalPlayerEntity> players) {
        return players == null ? List.of() : players.stream().map(MatchProposalPlayerEntity::getAccountId).toList();
    }
}
