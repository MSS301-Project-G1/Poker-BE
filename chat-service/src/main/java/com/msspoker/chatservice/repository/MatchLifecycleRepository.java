package com.msspoker.chatservice.repository;

import com.msspoker.chatservice.entity.MatchLifecycle;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchLifecycleRepository extends JpaRepository<MatchLifecycle, UUID> {
  @Modifying
  @Query(value = "INSERT INTO match_lifecycle(match_id) VALUES (:match) ON CONFLICT DO NOTHING", nativeQuery = true)
  int insertIfAbsent(@Param("match") UUID match);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select lifecycle from MatchLifecycle lifecycle where lifecycle.matchId=:match")
  Optional<MatchLifecycle> findLockedById(@Param("match") UUID match);

  default MatchLifecycle lock(UUID match) {
    insertIfAbsent(match);
    return findLockedById(match).orElseThrow();
  }
}
