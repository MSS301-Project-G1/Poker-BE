package com.msspoker.rankingservice.repository;

import com.msspoker.rankingservice.entity.RewardCreditEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RewardCreditRepository extends JpaRepository<RewardCreditEntity, UUID> {
    List<RewardCreditEntity> findTop20ByCreditedAtIsNullOrderByCreatedAtAscIdAsc();

    List<RewardCreditEntity> findByMatchIdOrderByPlace(UUID matchId);

    Optional<RewardCreditEntity> findByMatchIdAndAccountId(UUID matchId, UUID accountId);
}
