package com.msspoker.gameservice.repository;

import com.msspoker.gameservice.entity.MatchEntity;
import com.msspoker.gameservice.enums.GameMode;
import com.msspoker.gameservice.enums.MatchStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<MatchEntity, UUID> {
    Optional<MatchEntity> findByModeAndSourceId(GameMode mode, UUID sourceId);
    Optional<MatchEntity> findByTableId(UUID tableId);
    List<MatchEntity> findByStatus(MatchStatus status);
}
