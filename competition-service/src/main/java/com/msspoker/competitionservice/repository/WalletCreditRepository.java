package com.msspoker.competitionservice.repository;

import com.msspoker.competitionservice.entity.WalletCreditEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WalletCreditRepository extends JpaRepository<WalletCreditEntity, UUID> {
    List<WalletCreditEntity> findTop20ByCreditedAtIsNullOrderByCreatedAtAscIdAsc();

    boolean existsByIdempotencyKey(String idempotencyKey);
}
