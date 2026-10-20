package com.msspoker.authservice.repository;

import com.msspoker.authservice.entity.DailyCheckin;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyCheckinRepository extends JpaRepository<DailyCheckin, UUID> {
    boolean existsByAccountIdAndDate(UUID accountId, LocalDate date);

    List<DailyCheckin> findByAccountIdAndDateBetweenOrderByDateAsc(UUID accountId, LocalDate from, LocalDate to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM DailyCheckin c WHERE c.id = :id")
    Optional<DailyCheckin> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT c.id FROM DailyCheckin c WHERE c.rewardStatus = com.msspoker.authservice.enums.CheckinRewardStatus.PENDING "
            + "AND c.nextAttemptAt <= :now ORDER BY c.nextAttemptAt, c.id")
    List<UUID> findDueRewards(@Param("now") Instant now, Pageable limit);
}
