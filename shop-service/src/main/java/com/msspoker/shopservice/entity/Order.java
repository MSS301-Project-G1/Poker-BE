package com.msspoker.shopservice.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import com.msspoker.shopservice.enums.OrderStatus;
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
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", updatable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bundle_id", updatable = false)
    private CoinBundle bundle;

    @Column(name = "vip_coin_amount", updatable = false)
    private Long vipCoinAmount;

    @Column(name = "amount_vnd", nullable = false, updatable = false)
    private long amountVnd;

    @Column(name = "payos_order_code", nullable = false, unique = true, updatable = false)
    private long payosOrderCode;

    @Column(name = "checkout_url", length = 1000)
    private String checkoutUrl;

    @Column(name = "qr_code", columnDefinition = "TEXT")
    private String qrCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private OrderStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

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
