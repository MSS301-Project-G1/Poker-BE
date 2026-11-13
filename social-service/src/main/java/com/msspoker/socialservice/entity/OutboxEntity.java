package com.msspoker.socialservice.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "social_outbox")
@Getter
@Setter
@NoArgsConstructor
public class OutboxEntity {
    @Id private UUID id;
    private String body;
    private Instant occurredAt;
    private Instant publishedAt;
    private int attempts;
    private Instant nextAttemptAt;
    private String lastError;
}
