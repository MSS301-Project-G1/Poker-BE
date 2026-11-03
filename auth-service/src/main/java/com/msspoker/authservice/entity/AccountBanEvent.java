package com.msspoker.authservice.entity;

import com.msspoker.authservice.enums.AccountBanAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_ban_events")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AccountBanEvent {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 8)
    private AccountBanAction action;

    @Column(nullable = false, updatable = false, length = 500)
    private String reason;

    @Column(name = "until_at", updatable = false)
    private Instant until;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
}
