package com.msspoker.rankingservice.repository;

import com.msspoker.rankingservice.entity.TutorialCompletionEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface TutorialCompletionRepository extends JpaRepository<TutorialCompletionEntity, UUID> {
    /** 1 for the first completion, 0 afterwards; a concurrent duplicate waits on the key and then inserts nothing. */
    @Modifying
    @Query(value = """
            INSERT INTO tutorial_completions (account_id, reward_coins, completed_at)
            VALUES (:accountId, :rewardCoins, :completedAt)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(UUID accountId, long rewardCoins, Instant completedAt);
}
