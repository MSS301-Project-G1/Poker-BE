package com.msspoker.gameservice.persistence;

import com.msspoker.gameservice.engine.GameMode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MatchSettingsRepository extends JpaRepository<MatchSettingsEntity, UUID> {
    Optional<MatchSettingsEntity> findByMode(GameMode mode);
}
