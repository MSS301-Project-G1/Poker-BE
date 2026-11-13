package com.msspoker.competitionservice.repository;

import com.msspoker.competitionservice.entity.WalletCreditEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WalletCreditRepository extends JpaRepository<WalletCreditEntity, UUID> {
    /** Fewest attempts first, so a row the other service keeps refusing never holds back newer ones. */
    List<WalletCreditEntity> findTop20ByCreditedAtIsNullOrderByAttemptsAscCreatedAtAscIdAsc();

    boolean existsByIdempotencyKey(String idempotencyKey);
}
