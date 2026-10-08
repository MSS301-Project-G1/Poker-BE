package com.msspoker.gameservice.repository;

import com.msspoker.gameservice.entity.MatchPlayerEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MatchPlayerRepository extends JpaRepository<MatchPlayerEntity, UUID> {
    Page<MatchPlayerEntity> findByAccountIdAndIsDeletedFalse(UUID accountId, Pageable pageable);
    List<MatchPlayerEntity> findByMatchIdIn(Collection<UUID> matchIds);
    List<MatchPlayerEntity> findByMatchIdOrderBySeatIndex(UUID matchId);
}
