package com.msspoker.shopservice.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.f4b6a3.uuid.UuidCreator;
import com.msspoker.shopservice.enums.ProductTier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class Product {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "name", length = 150, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", length = 30, nullable = false)
    private ProductTier tier;

    @Column(name = "price_vnd", nullable = false)
    private long priceVnd;

    @Column(name = "price_vip_coin", nullable = false)
    private long priceVipCoin;

    @Column(name = "asset_url", length = 500, nullable = false)
    private String assetUrl;

    @Column(name = "preview_url", length = 500)
    private String previewUrl;

    @Column(name = "preview_video_url", length = 500)
    private String previewVideoUrl;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "json")
    private String metadata;

    @JsonProperty("isExclusive")
    @Column(name = "is_exclusive", nullable = false)
    @Builder.Default
    private boolean isExclusive = false;

    @JsonProperty("isDeleted")
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    @Column(name = "sale_expires_at")
    private Instant saleExpiresAt;

    @JsonProperty("exclusive")
    public void setExclusiveAlias(Boolean exclusive) {
        if (exclusive != null) {
            this.isExclusive = exclusive;
        }
    }

    @JsonProperty("deleted")
    public void setDeletedAlias(Boolean deleted) {
        if (deleted != null) {
            this.isDeleted = deleted;
        }
    }

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
