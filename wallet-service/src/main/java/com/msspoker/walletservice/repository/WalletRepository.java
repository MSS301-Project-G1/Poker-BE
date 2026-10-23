package com.msspoker.walletservice.repository;

import com.msspoker.walletservice.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByAccountId(UUID accountId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT w FROM Wallet w WHERE w.accountId = :accountId")
    Optional<Wallet> findByAccountIdForUpdate(@org.springframework.data.repository.query.Param("accountId") UUID accountId);

    boolean existsByAccountId(UUID accountId);
}
