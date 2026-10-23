package com.msspoker.rankingservice.repository;

import com.msspoker.rankingservice.entity.SeasonTierRewardEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SeasonTierRewardRepository extends JpaRepository<SeasonTierRewardEntity, UUID> {
    List<SeasonTierRewardEntity> findBySeasonId(UUID seasonId);

    List<SeasonTierRewardEntity> findBySeasonIdIn(Collection<UUID> seasonIds);

    void deleteBySeasonId(UUID seasonId);
}
