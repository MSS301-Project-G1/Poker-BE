package com.msspoker.rankingservice.repository;

import com.msspoker.rankingservice.entity.SeasonEntity;
import com.msspoker.rankingservice.enums.SeasonStatus;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeasonRepository extends JpaRepository<SeasonEntity, UUID> {
    Optional<SeasonEntity> findFirstByStatusAndIsDeletedFalse(SeasonStatus status);

    /** The next season to activate: earliest start first. */
    Optional<SeasonEntity> findFirstByStatusAndIsDeletedFalseOrderByStartAtAscIdAsc(SeasonStatus status);

    List<SeasonEntity> findByIsDeletedFalseOrderByStartAtDescIdDesc();

    Optional<SeasonEntity> findByIdAndIsDeletedFalse(UUID id);

    /**
     * Elo updates and season closing both lock the active season row, so a match result is
     * never written into a season that is being closed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SeasonEntity s where s.status = :status and s.isDeleted = false")
    List<SeasonEntity> lockByStatus(SeasonStatus status);
}
