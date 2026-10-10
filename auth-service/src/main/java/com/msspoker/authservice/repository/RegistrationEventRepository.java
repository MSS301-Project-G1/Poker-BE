package com.msspoker.authservice.repository;

import com.msspoker.authservice.entity.RegistrationEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RegistrationEventRepository extends JpaRepository<RegistrationEvent, UUID> {
    // Multiple auth instances can dispatch different rows without publishing one row concurrently.
    @Query(value = """
            SELECT * FROM account_registration_events
            WHERE published_at IS NULL
            ORDER BY occurred_at
            LIMIT 20 FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<RegistrationEvent> findPendingForUpdate();
}
