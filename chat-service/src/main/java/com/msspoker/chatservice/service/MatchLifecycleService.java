package com.msspoker.chatservice.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MatchLifecycleService {
    void started(UUID match, List<UUID> players);

    void finished(UUID match, Instant at);
}
