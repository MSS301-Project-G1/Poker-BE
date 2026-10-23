package com.msspoker.authservice.repository;

import com.msspoker.authservice.entity.AvatarPreset;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AvatarPresetRepository extends JpaRepository<AvatarPreset, UUID> {
    List<AvatarPreset> findAllByActiveTrueOrderByCreatedAtAscIdAsc();

    List<AvatarPreset> findAllByOrderByCreatedAtAscIdAsc();

    Optional<AvatarPreset> findByObjectKey(String objectKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AvatarPreset a WHERE a.id = :id")
    Optional<AvatarPreset> findByIdForUpdate(@Param("id") UUID id);
}
