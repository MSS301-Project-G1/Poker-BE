package com.msspoker.competitionservice.repository;

import com.msspoker.competitionservice.entity.ItemGrantEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ItemGrantRepository extends JpaRepository<ItemGrantEntity, UUID> {
    List<ItemGrantEntity> findTop20ByGrantedAtIsNullOrderByCreatedAtAscIdAsc();
}
