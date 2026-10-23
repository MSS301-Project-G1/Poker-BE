package com.msspoker.chatservice.entity;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "processed_events")
@Getter
@NoArgsConstructor
public class ProcessedEvent {
  @Id
  @Column(name = "event_id")
  private UUID eventId;
  @Column(name = "event_type")
  private String eventType;
  @Column(name = "processed_at")
  private java.time.Instant processedAt;
}
