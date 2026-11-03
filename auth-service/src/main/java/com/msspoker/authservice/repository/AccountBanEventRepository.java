package com.msspoker.authservice.repository;

import com.msspoker.authservice.entity.AccountBanEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AccountBanEventRepository extends JpaRepository<AccountBanEvent, UUID> {
    Page<AccountBanEvent> findByAccountId(UUID accountId, Pageable pageable);
}
