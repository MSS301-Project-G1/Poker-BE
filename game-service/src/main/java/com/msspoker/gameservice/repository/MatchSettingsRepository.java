package com.msspoker.gameservice.repository;

import com.msspoker.gameservice.entity.MatchSettingsEntity;
import com.msspoker.gameservice.enums.GameMode;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MatchSettingsRepository extends JpaRepository<MatchSettingsEntity, UUID> {
    Optional<MatchSettingsEntity> findByMode(GameMode mode);
}
