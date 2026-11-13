package com.msspoker.matchmakingservice.repository;

import com.msspoker.matchmakingservice.entity.PartyNotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

public interface PartyNotificationRepository extends JpaRepository<PartyNotificationEntity, UUID> {
    @Query(value = "SELECT * FROM party_notification_outbox WHERE published_at IS NULL ORDER BY created_at LIMIT 50 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<PartyNotificationEntity> pending();
}
