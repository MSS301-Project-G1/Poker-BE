package com.msspoker.chatservice.repository;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChatLockRepository {
  private final JdbcTemplate jdbc;

  public void lock(String key) {
    jdbc.update("INSERT INTO chat_locks(lock_key) VALUES (?) ON CONFLICT DO NOTHING", key);
    jdbc.queryForObject(
        "SELECT lock_key FROM chat_locks WHERE lock_key=? FOR UPDATE", String.class, key);
  }

  public Instant lifecycle(UUID match) {
    jdbc.update("INSERT INTO match_lifecycle(match_id) VALUES (?) ON CONFLICT DO NOTHING", match);
    return jdbc.queryForObject(
        "SELECT finished_at FROM match_lifecycle WHERE match_id=? FOR UPDATE",
        (rs, n) -> rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant(),
        match);
  }

  public void finish(UUID match, Instant at) {
    jdbc.update(
        "UPDATE match_lifecycle SET finished_at=COALESCE(finished_at,?) WHERE match_id=?",
        java.sql.Timestamp.from(at),
        match);
  }
}
