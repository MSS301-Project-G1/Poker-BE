package com.msspoker.authservice.repository;

import com.msspoker.authservice.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    // Callers must pass the canonical (stripped, lowercase) email.
    Optional<Account> findByEmail(String email);

    Optional<Account> findByEmailAndDeletedFalse(String email);

    Optional<Account> findByIdAndDeletedFalse(UUID accountId);
}
