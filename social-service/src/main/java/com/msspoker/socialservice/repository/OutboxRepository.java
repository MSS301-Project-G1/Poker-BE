package com.msspoker.socialservice.repository;

import com.msspoker.socialservice.entity.OutboxEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEntity, UUID> {
    @Query(
            value =
                    "SELECT * FROM social_outbox WHERE published_at IS NULL AND"
                        + " next_attempt_at<=CURRENT_TIMESTAMP ORDER BY occurred_at,id LIMIT 20 FOR"
                        + " UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<OutboxEntity> pendingLocked();
}
