package com.msspoker.rankingservice.repository;

import com.msspoker.rankingservice.entity.RewardGrantEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RewardGrantRepository extends JpaRepository<RewardGrantEntity, UUID> {
    List<RewardGrantEntity> findTop20ByGrantedAtIsNullOrderByCreatedAtAscIdAsc();

    List<RewardGrantEntity> findByAccountIdOrderByCreatedAtAscIdAsc(UUID accountId);
}
