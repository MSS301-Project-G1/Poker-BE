package com.msspoker.chatservice.repository;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor
public class EventReceiptRepositoryCustomImpl implements EventReceiptRepositoryCustom {
  private final JdbcTemplate jdbc;

  public boolean claim(UUID id, String type) {
    return jdbc.update(
            "INSERT INTO processed_events(event_id,event_type) VALUES (?,?) ON CONFLICT DO NOTHING",
            id,
            type)
        == 1;
  }
}
