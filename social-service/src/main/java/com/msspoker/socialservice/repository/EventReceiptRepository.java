package com.msspoker.socialservice.repository;

import com.msspoker.socialservice.entity.ProcessedEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventReceiptRepository extends JpaRepository<ProcessedEvent, UUID> {
  @Modifying
  @Query(value = "INSERT INTO processed_events(event_id,event_type) VALUES (:id,:type) ON CONFLICT DO NOTHING", nativeQuery = true)
  int insertIfAbsent(@Param("id") UUID id, @Param("type") String type);

  default boolean claim(UUID id, String type) {
    return insertIfAbsent(id, type) == 1;
  }
}
