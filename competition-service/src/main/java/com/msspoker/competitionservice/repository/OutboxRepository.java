package com.msspoker.competitionservice.repository;

import com.msspoker.competitionservice.entity.OutboxEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEntity, UUID> {
    List<OutboxEntity> findTop20ByPublishedAtIsNullOrderByOccurredAtAscIdAsc();
}
