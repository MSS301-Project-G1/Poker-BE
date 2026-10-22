package com.msspoker.socialservice.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "friendships")
@Getter
@Setter
@NoArgsConstructor
public class Friendship {
  @Id private UUID id;
  private UUID accountId;
  private UUID friendId;
  private Instant createdAt;
  private Instant updatedAt;
  private boolean isDeleted;
}
