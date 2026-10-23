package com.msspoker.rankingservice.repository;

import com.msspoker.rankingservice.entity.EloHistoryEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EloHistoryRepository extends JpaRepository<EloHistoryEntity, UUID> {
    boolean existsByMatchId(UUID matchId);

    List<EloHistoryEntity> findByMatchIdOrderByPlace(UUID matchId);

    Optional<EloHistoryEntity> findByMatchIdAndAccountId(UUID matchId, UUID accountId);
}
