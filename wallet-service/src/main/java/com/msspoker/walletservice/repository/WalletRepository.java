package com.msspoker.walletservice.repository;

import com.msspoker.walletservice.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByAccountId(UUID accountId);

    boolean existsByAccountId(UUID accountId);
}
