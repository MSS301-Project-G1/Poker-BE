package com.msspoker.socialservice.repository;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RelationLockRepository {
  private final JdbcTemplate jdbc;

  public static String key(UUID a, UUID b) {
    return a.toString().compareTo(b.toString()) < 0 ? a + ":" + b : b + ":" + a;
  }

  public void lock(UUID a, UUID b) {
    String key = key(a, b);
    jdbc.update("INSERT INTO relation_locks(pair_key) VALUES (?) ON CONFLICT DO NOTHING", key);
    jdbc.queryForObject(
        "SELECT pair_key FROM relation_locks WHERE pair_key=? FOR UPDATE", String.class, key);
  }
}
