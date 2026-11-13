package com.msspoker.authservice.entity;

import com.msspoker.authservice.enums.AccountModerationAction;
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
@Table(name = "account_moderation_events")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AccountModerationEvent {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private AccountModerationAction action;

    @Column(nullable = false, updatable = false, length = 500)
    private String reason;

    /** What the action removed (old display name or bio), kept so the change can be reviewed. */
    @Column(name = "previous_value", updatable = false, length = 500)
    private String previousValue;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
}
