package com.msspoker.rankingservice.repository;

import com.msspoker.rankingservice.entity.EloAdjustmentEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EloAdjustmentRepository extends JpaRepository<EloAdjustmentEntity, UUID> {
    Page<EloAdjustmentEntity> findByAccountId(UUID accountId, Pageable pageable);
}
