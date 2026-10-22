package com.msspoker.socialservice.repository;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor
public class RelationLockRepositoryCustomImpl implements RelationLockRepositoryCustom {
  private final JdbcTemplate jdbc;

  public void lock(UUID a, UUID b) {
    String key = RelationLockRepository.key(a, b);
    jdbc.update("INSERT INTO relation_locks(pair_key) VALUES (?) ON CONFLICT DO NOTHING", key);
    jdbc.queryForObject(
        "SELECT pair_key FROM relation_locks WHERE pair_key=? FOR UPDATE", String.class, key);
  }
}
