package com.msspoker.shopservice.repository;

import com.msspoker.shopservice.entity.AiPromptPreset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiPromptPresetRepository extends JpaRepository<AiPromptPreset, UUID> {

    List<AiPromptPreset> findByCategoryCodeAndIsActiveTrue(String categoryCode);

    List<AiPromptPreset> findByIsActiveTrue();

    Optional<AiPromptPreset> findByPresetName(String presetName);
}
