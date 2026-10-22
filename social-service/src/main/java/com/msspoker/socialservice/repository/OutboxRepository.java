package com.msspoker.socialservice.repository;

import com.msspoker.socialservice.entity.OutboxEvent;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
  @Query(
      value =
          "SELECT * FROM social_outbox WHERE published_at IS NULL AND"
              + " next_attempt_at<=CURRENT_TIMESTAMP ORDER BY occurred_at,id LIMIT 20 FOR UPDATE"
              + " SKIP LOCKED",
      nativeQuery = true)
  List<OutboxEvent> pendingLocked();
}
