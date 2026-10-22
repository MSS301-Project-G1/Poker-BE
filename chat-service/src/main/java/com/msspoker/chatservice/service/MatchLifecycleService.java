package com.msspoker.chatservice.service;

import java.util.*;
import java.time.Instant;

public interface MatchLifecycleService {
  void started(UUID match, List<UUID> players);
  void finished(UUID match, Instant at);
}
