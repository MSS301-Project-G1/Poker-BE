package com.msspoker.competitionservice.repository;

import com.msspoker.competitionservice.entity.TournamentTableEntity;
import com.msspoker.competitionservice.enums.TableStatus;
import com.msspoker.competitionservice.enums.TournamentRound;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TournamentTableRepository extends JpaRepository<TournamentTableEntity, UUID> {
    List<TournamentTableEntity> findByTournamentIdOrderByRoundAscTableIndexAsc(UUID tournamentId);

    List<TournamentTableEntity> findByTournamentIdAndRoundOrderByTableIndexAsc(UUID tournamentId, TournamentRound round);

    List<TournamentTableEntity> findTop10ByStatusOrderByCreatedAtAscIdAsc(TableStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TournamentTableEntity t where t.id = :id")
    Optional<TournamentTableEntity> lockById(UUID id);
}
