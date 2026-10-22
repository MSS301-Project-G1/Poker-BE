package com.msspoker.chatservice.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
public class Conversation {
  @Id private UUID id;
  private String type;
  private UUID refId;
  private String directKey;
  private Instant closedAt;
  private Instant createdAt;
  private Instant updatedAt;
  private boolean isDeleted;
}
