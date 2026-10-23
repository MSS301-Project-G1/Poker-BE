package com.msspoker.socialservice.repository;

import com.msspoker.socialservice.entity.Friendship;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {
  Optional<Friendship> findByAccountIdAndFriendId(UUID a, UUID b);

  boolean existsByAccountIdAndFriendIdAndIsDeletedFalse(UUID a, UUID b);

  Page<Friendship> findByAccountIdAndIsDeletedFalse(UUID id, Pageable page);

  List<Friendship> findByAccountIdAndIsDeletedFalse(UUID id);
}
