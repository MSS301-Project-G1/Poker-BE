package com.msspoker.chatservice.repository;

import java.util.UUID;
import java.time.Instant;

public interface ChatLockRepositoryCustom {
  void lock(String key);
  Instant lifecycle(UUID match);
  void finish(UUID match, Instant at);
}
