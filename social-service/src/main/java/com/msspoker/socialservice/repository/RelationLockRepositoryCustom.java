package com.msspoker.socialservice.repository;

import java.util.UUID;

public interface RelationLockRepositoryCustom {
  void lock(UUID a, UUID b);
}
