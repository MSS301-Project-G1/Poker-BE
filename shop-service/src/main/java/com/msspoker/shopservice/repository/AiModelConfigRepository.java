package com.msspoker.shopservice.repository;

import com.msspoker.shopservice.entity.AiModelConfig;
import com.msspoker.shopservice.enums.AiProviderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiModelConfigRepository extends JpaRepository<AiModelConfig, UUID> {

    List<AiModelConfig> findByIsActiveTrue();

    Optional<AiModelConfig> findByIsDefaultTrue();

    Optional<AiModelConfig> findByProviderAndModelName(AiProviderType provider, String modelName);

    @Modifying
    @Query("UPDATE AiModelConfig c SET c.isDefault = false WHERE c.id != :id")
    void clearDefaultExcept(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE AiModelConfig c SET c.isDefault = false")
    void clearAllDefaults();
}
