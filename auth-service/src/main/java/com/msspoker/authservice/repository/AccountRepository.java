package com.msspoker.authservice.repository;

import com.msspoker.authservice.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    @EntityGraph(attributePaths = "profile")
    @Query("""
            SELECT a FROM Account a LEFT JOIN a.profile p
            WHERE a.deleted = false
            AND (:keyword = '' OR LOCATE(LOWER(:keyword), LOWER(a.email)) > 0
                OR LOCATE(LOWER(:keyword), LOWER(p.displayName)) > 0
                OR LOCATE(LOWER(:keyword), LOWER(p.playerCode)) > 0)
            AND (:banned IS NULL
                OR (:banned = true AND a.banned = true AND (a.bannedUntil IS NULL OR a.bannedUntil > :now))
                OR (:banned = false AND (a.banned = false OR (a.bannedUntil IS NOT NULL AND a.bannedUntil <= :now))))
            """)
    Page<Account> searchAdminUsers(@Param("keyword") String keyword, @Param("banned") Boolean banned,
            @Param("now") Instant now, Pageable pageable);

    // Callers must pass the canonical (stripped, lowercase) email.
    Optional<Account> findByEmail(String email);

    Optional<Account> findByEmailAndDeletedFalse(String email);

    Optional<Account> findByIdAndDeletedFalse(UUID accountId);

    // Login, recovery, verification and resends serialize changes through the account row.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.email = :email")
    Optional<Account> findByEmailForUpdate(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id AND a.deleted = false")
    Optional<Account> findByIdForUpdate(@Param("id") UUID accountId);
}
