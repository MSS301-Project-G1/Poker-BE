package com.msspoker.shopservice.repository;

import com.msspoker.shopservice.entity.AssetGenerationBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AssetGenerationBatchRepository extends JpaRepository<AssetGenerationBatch, UUID> {

    @Query("SELECT b FROM AssetGenerationBatch b WHERE b.status = :status AND b.expiresAt <= :now")
    List<AssetGenerationBatch> findByStatusAndExpiresAtLessThanEqual(
            @Param("status") String status,
            @Param("now") Instant now
    );

    List<AssetGenerationBatch> findTop20ByOrderByCreatedAtDesc();

    List<AssetGenerationBatch> findByProductId(UUID productId);
}
