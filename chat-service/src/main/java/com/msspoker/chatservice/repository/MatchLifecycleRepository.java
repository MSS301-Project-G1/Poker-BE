package com.msspoker.chatservice.repository;

import com.msspoker.chatservice.entity.MatchLifecycleEntity;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MatchLifecycleRepository extends JpaRepository<MatchLifecycleEntity, UUID> {
    @Modifying
    @Query(
            value = "INSERT INTO match_lifecycle(match_id) VALUES (:match) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(@Param("match") UUID match);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lifecycle from MatchLifecycleEntity lifecycle where lifecycle.matchId=:match")
    Optional<MatchLifecycleEntity> findLockedById(@Param("match") UUID match);

    default MatchLifecycleEntity lock(UUID match) {
        insertIfAbsent(match);
        return findLockedById(match).orElseThrow();
    }
}
