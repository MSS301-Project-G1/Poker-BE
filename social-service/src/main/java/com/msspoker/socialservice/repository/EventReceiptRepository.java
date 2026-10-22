package com.msspoker.socialservice.repository;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class EventReceiptRepository {
  private final JdbcTemplate jdbc;

  public boolean claim(UUID id, String type) {
    return jdbc.update(
            "INSERT INTO processed_events(event_id,event_type) VALUES (?,?) ON CONFLICT DO NOTHING",
            id,
            type)
        == 1;
  }
}
