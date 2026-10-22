package com.msspoker.socialservice.repository;

import com.msspoker.socialservice.entity.RelationLock;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RelationLockRepository extends JpaRepository<RelationLock, String>, RelationLockRepositoryCustom {
  static String key(UUID a, UUID b) {
    return a.toString().compareTo(b.toString()) < 0 ? a + ":" + b : b + ":" + a;
  }

}
