package com.msspoker.socialservice.repository;

import com.msspoker.socialservice.entity.FriendRequest;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, UUID> {
  Optional<FriendRequest> findByPairKeyAndStatusAndIsDeletedFalse(String key, String status);

  Page<FriendRequest> findByAddresseeIdAndIsDeletedFalse(UUID id, Pageable page);

  Page<FriendRequest> findByRequesterIdAndIsDeletedFalse(UUID id, Pageable page);
}
