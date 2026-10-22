package com.msspoker.socialservice.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "friend_requests")
@Getter
@Setter
@NoArgsConstructor
public class FriendRequest {
  @Id private UUID id;
  private UUID requesterId;
  private UUID addresseeId;
  private String pairKey;
  private String status;
  private Instant createdAt;
  private Instant updatedAt;
  private boolean isDeleted;
}
