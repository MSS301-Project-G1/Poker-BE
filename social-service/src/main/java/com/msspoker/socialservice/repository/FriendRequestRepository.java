package com.msspoker.socialservice.repository;

import com.msspoker.socialservice.entity.FriendRequestEntity;
import com.msspoker.socialservice.enums.FriendRequestStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FriendRequestRepository extends JpaRepository<FriendRequestEntity, UUID> {
    Optional<FriendRequestEntity> findByPairKeyAndStatusAndIsDeletedFalse(
            String key, FriendRequestStatus status);

    Page<FriendRequestEntity> findByAddresseeIdAndIsDeletedFalse(UUID id, Pageable page);

    Page<FriendRequestEntity> findByRequesterIdAndIsDeletedFalse(UUID id, Pageable page);
}
