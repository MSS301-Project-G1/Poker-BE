package com.msspoker.authservice.repository;

import com.msspoker.authservice.entity.AccountModerationEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AccountModerationEventRepository extends JpaRepository<AccountModerationEvent, UUID> {
    Page<AccountModerationEvent> findByAccountId(UUID accountId, Pageable pageable);
}
