package com.msspoker.socialservice.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "social_outbox")
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent {
  @Id private UUID id;
  private String body;
  private Instant occurredAt;
  private Instant publishedAt;
  private int attempts;
  private Instant nextAttemptAt;
  private String lastError;
}
