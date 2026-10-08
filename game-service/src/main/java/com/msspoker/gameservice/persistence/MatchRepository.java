package com.msspoker.gameservice.persistence;

import com.msspoker.gameservice.engine.GameMode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<MatchEntity, UUID> {
    Optional<MatchEntity> findByModeAndSourceId(GameMode mode, UUID sourceId);
    Optional<MatchEntity> findByTableId(UUID tableId);
    List<MatchEntity> findByStatus(MatchStatus status);
}
