package com.msspoker.matchmakingservice.repository;

import com.msspoker.matchmakingservice.entity.MatchProposalEntity;
import com.msspoker.matchmakingservice.enums.MatchMode;
import com.msspoker.matchmakingservice.enums.ProposalStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MatchProposalRepository extends JpaRepository<MatchProposalEntity, UUID> {
    @EntityGraph(attributePaths = "players")
    List<MatchProposalEntity> findTop20ByModeAndStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            MatchMode mode,
            ProposalStatus status,
            Instant updatedBefore
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select proposal from MatchProposalEntity proposal where proposal.id = :id and proposal.isDeleted = false")
    java.util.Optional<MatchProposalEntity> findLockedById(@Param("id") UUID id);

    List<MatchProposalEntity> findTop20ByModeAndStatusAndAcceptDeadlineBeforeOrderByAcceptDeadlineAsc(
            MatchMode mode,
            ProposalStatus status,
            Instant deadline
    );

    @EntityGraph(attributePaths = "players")
    List<MatchProposalEntity> findTop20ByModeAndStatusInAndRedisReconciledAtIsNullOrderByUpdatedAtAsc(
            MatchMode mode,
            java.util.Collection<ProposalStatus> statuses
    );
}
