package com.msspoker.walletservice.repository;

import com.msspoker.walletservice.entity.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {

    Optional<WalletTransaction> findByIdempotencyKey(String idempotencyKey);

    Page<WalletTransaction> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);
}
