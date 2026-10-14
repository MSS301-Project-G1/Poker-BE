package com.msspoker.authservice.repository;

import com.msspoker.authservice.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    // Callers must pass the canonical (stripped, lowercase) email.
    Optional<Account> findByEmail(String email);

    Optional<Account> findByEmailAndDeletedFalse(String email);

    Optional<Account> findByIdAndDeletedFalse(UUID accountId);

    // Verification and resends share this lock to serialize changes to the current OTP.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.email = :email")
    Optional<Account> findByEmailForUpdate(@Param("email") String email);
}
