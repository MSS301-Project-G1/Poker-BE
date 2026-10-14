package com.msspoker.competitionservice.repository;

import com.msspoker.competitionservice.entity.TournamentSeatEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TournamentSeatRepository extends JpaRepository<TournamentSeatEntity, UUID> {
    List<TournamentSeatEntity> findByTournamentTableIdOrderBySeatIndex(UUID tournamentTableId);

    List<TournamentSeatEntity> findByTournamentTableIdIn(Collection<UUID> tournamentTableIds);
}
