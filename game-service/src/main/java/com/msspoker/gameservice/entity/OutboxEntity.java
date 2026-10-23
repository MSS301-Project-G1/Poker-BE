package com.msspoker.gameservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "game_outbox")
@Getter
@Setter
public class OutboxEntity {
    @Id private UUID id;
    private String eventType;
    @Column(columnDefinition = "text") private String body;
    private Instant occurredAt;
    private Instant publishedAt;
}
