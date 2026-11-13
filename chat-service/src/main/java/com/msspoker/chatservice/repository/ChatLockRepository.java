package com.msspoker.chatservice.repository;

import com.msspoker.chatservice.entity.ChatLockEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatLockRepository extends JpaRepository<ChatLockEntity, String> {
    @Modifying
    @Query(
            value = "INSERT INTO chat_locks(lock_key) VALUES (:key) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(@Param("key") String key);

    @Query(
            value = "SELECT lock_key FROM chat_locks WHERE lock_key=:key FOR UPDATE",
            nativeQuery = true)
    String lockKey(@Param("key") String key);

    default void lock(String key) {
        insertIfAbsent(key);
        lockKey(key);
    }
}
