package com.msspoker.chatservice.service.client;

import java.util.UUID;

public interface SocialClient {
  record Relation(boolean isFriend, boolean isBlocked) {}

  Relation relation(UUID a, UUID b);
}
