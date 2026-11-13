package com.msspoker.rankingservice.repository;

import com.msspoker.rankingservice.entity.RewardCreditEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RewardCreditRepository extends JpaRepository<RewardCreditEntity, UUID> {
    /** Fewest attempts first, so a row the other service keeps refusing never holds back newer ones. */
    List<RewardCreditEntity> findTop20ByCreditedAtIsNullOrderByAttemptsAscCreatedAtAscIdAsc();

    List<RewardCreditEntity> findByMatchIdOrderByPlace(UUID matchId);

    Optional<RewardCreditEntity> findByMatchIdAndAccountId(UUID matchId, UUID accountId);
}
