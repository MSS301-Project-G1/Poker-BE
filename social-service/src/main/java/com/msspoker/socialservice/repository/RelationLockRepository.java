package com.msspoker.socialservice.repository;

import com.msspoker.socialservice.entity.RelationLockEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface RelationLockRepository extends JpaRepository<RelationLockEntity, String> {
    static String key(UUID a, UUID b) {
        return a.toString().compareTo(b.toString()) < 0 ? a + ":" + b : b + ":" + a;
    }

    @Modifying
    @Query(
            value = "INSERT INTO relation_locks(pair_key) VALUES (:key) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(@Param("key") String key);

    @Query(
            value = "SELECT pair_key FROM relation_locks WHERE pair_key=:key FOR UPDATE",
            nativeQuery = true)
    String lockKey(@Param("key") String key);

    default void lock(UUID a, UUID b) {
        String pairKey = key(a, b);
        insertIfAbsent(pairKey);
        lockKey(pairKey);
    }
}
