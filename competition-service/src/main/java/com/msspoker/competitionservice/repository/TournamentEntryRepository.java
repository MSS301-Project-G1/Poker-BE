package com.msspoker.competitionservice.repository;

import com.msspoker.competitionservice.entity.TournamentEntryEntity;
import com.msspoker.competitionservice.enums.EntryStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TournamentEntryRepository extends JpaRepository<TournamentEntryEntity, UUID> {
    List<TournamentEntryEntity> findByTournamentIdOrderByRegisteredAtAscIdAsc(UUID tournamentId);

    List<TournamentEntryEntity> findByTournamentIdAndStatus(UUID tournamentId, EntryStatus status);

    List<TournamentEntryEntity> findByTournamentIdAndAccountIdIn(UUID tournamentId, Collection<UUID> accountIds);

    Optional<TournamentEntryEntity> findByTournamentIdAndAccountId(UUID tournamentId, UUID accountId);

    long countByTournamentId(UUID tournamentId);

    /** Entries that hold a place: everything except expired ELITE invitations. */
    long countByTournamentIdAndStatusNot(UUID tournamentId, EntryStatus status);
}
