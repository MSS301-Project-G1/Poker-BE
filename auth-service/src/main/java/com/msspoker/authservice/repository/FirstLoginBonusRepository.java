package com.msspoker.authservice.repository;

import com.msspoker.authservice.entity.FirstLoginBonus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FirstLoginBonusRepository extends JpaRepository<FirstLoginBonus, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM FirstLoginBonus b WHERE b.id = :id")
    Optional<FirstLoginBonus> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT b.id FROM FirstLoginBonus b WHERE b.rewardStatus = com.msspoker.authservice.enums.FirstLoginBonusStatus.PENDING "
            + "AND b.nextAttemptAt <= :now ORDER BY b.nextAttemptAt, b.id")
    List<UUID> findDueBonuses(@Param("now") Instant now, Pageable limit);
}
