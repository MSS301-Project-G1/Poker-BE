package com.msspoker.socialservice.repository;

import com.msspoker.socialservice.entity.FriendshipEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FriendshipRepository extends JpaRepository<FriendshipEntity, UUID> {
    Optional<FriendshipEntity> findByAccountIdAndFriendId(UUID a, UUID b);

    boolean existsByAccountIdAndFriendIdAndIsDeletedFalse(UUID a, UUID b);

    Page<FriendshipEntity> findByAccountIdAndIsDeletedFalse(UUID id, Pageable page);

    List<FriendshipEntity> findByAccountIdAndIsDeletedFalse(UUID id);
}
