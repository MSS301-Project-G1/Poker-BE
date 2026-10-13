package com.msspoker.rankingservice.repository;

import com.msspoker.rankingservice.entity.SeasonRewardClaimEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeasonRewardClaimRepository extends JpaRepository<SeasonRewardClaimEntity, UUID> {
    Optional<SeasonRewardClaimEntity> findBySeasonIdAndAccountId(UUID seasonId, UUID accountId);

    List<SeasonRewardClaimEntity> findByAccountId(UUID accountId);
}
