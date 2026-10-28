package com.msspoker.authservice.repository;

import com.msspoker.authservice.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProfileRepository extends JpaRepository<Profile, UUID> {
    @Query("""
            SELECT p FROM Profile p WHERE p.account.deleted = false
            AND p.account.emailVerified = true AND p.account.accountStatus = :status
            AND p.accountId <> :actor
            AND (p.playerCode = :code OR LOCATE(LOWER(:term), LOWER(p.displayName)) > 0)
            ORDER BY p.displayName, p.playerCode
            """)
    List<Profile> searchPlayers(@Param("actor") UUID actor, @Param("term") String term,
            @Param("code") String code,
            @Param("status") com.msspoker.authservice.enums.AccountStatus status,
            org.springframework.data.domain.Pageable pageable);

    Optional<Profile> findByAccountId(UUID accountId);

    Optional<Profile> findByAccountIdAndAccount_DeletedFalse(UUID accountId);

    List<Profile> findAllByAccountIdInAndAccount_DeletedFalse(Collection<UUID> accountIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Profile p WHERE p.accountId = :accountId")
    Optional<Profile> findByAccountIdForUpdate(@Param("accountId") UUID accountId);
}
