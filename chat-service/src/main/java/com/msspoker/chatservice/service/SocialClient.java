package com.msspoker.chatservice.service;

import java.util.UUID;

public interface SocialClient {
  record Relation(boolean isFriend, boolean isBlocked) {}

  Relation relation(UUID a, UUID b);
}
