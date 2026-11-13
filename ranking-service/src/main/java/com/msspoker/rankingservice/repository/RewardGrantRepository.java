package com.msspoker.rankingservice.repository;

import com.msspoker.rankingservice.entity.RewardGrantEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RewardGrantRepository extends JpaRepository<RewardGrantEntity, UUID> {
    /** Fewest attempts first, so a row the other service keeps refusing never holds back newer ones. */
    List<RewardGrantEntity> findTop20ByGrantedAtIsNullOrderByAttemptsAscCreatedAtAscIdAsc();

    List<RewardGrantEntity> findByAccountIdOrderByCreatedAtAscIdAsc(UUID accountId);
}
