package com.msspoker.shopservice.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "asset_generation_batches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetGenerationBatch {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "category_code", length = 50, nullable = false)
    private String categoryCode;

    @Column(name = "product_id")
    private UUID productId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_config_id")
    private AiModelConfig modelConfig;

    @Column(name = "raw_prompt", columnDefinition = "TEXT", nullable = false)
    private String rawPrompt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "style_spec", columnDefinition = "json", nullable = false)
    private String styleSpec;

    @Column(name = "s3_zip_url", length = 500, nullable = false)
    private String s3ZipUrl;

    @Column(name = "s3_preview_url", length = 500, nullable = false)
    private String s3PreviewUrl;

    @Column(name = "status", length = 30, nullable = false)
    @Builder.Default
    private String status = "GENERATED"; // GENERATED, APPLIED, DELETED, EXPIRED

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.expiresAt == null) {
            this.expiresAt = now.plus(24, ChronoUnit.HOURS);
        }
    }
}
