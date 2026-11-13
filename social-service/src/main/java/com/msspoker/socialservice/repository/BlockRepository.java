package com.msspoker.socialservice.repository;

import com.msspoker.socialservice.entity.BlockEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BlockRepository extends JpaRepository<BlockEntity, UUID> {
    Optional<BlockEntity> findByBlockerIdAndBlockedId(UUID a, UUID b);

    boolean existsByBlockerIdAndBlockedIdAndIsDeletedFalse(UUID a, UUID b);

    Page<BlockEntity> findByBlockerIdAndIsDeletedFalse(UUID id, Pageable page);
}
