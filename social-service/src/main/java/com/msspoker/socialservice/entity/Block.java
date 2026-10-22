package com.msspoker.socialservice.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "blocks")
@Getter
@Setter
@NoArgsConstructor
public class Block {
  @Id private UUID id;
  private UUID blockerId;
  private UUID blockedId;
  private Instant createdAt;
  private Instant updatedAt;
  private boolean isDeleted;
}
