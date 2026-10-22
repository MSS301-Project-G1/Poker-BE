package com.msspoker.chatservice.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "conversation_members")
@Getter
@Setter
@NoArgsConstructor
public class ConversationMember {
  @Id private UUID id;
  private UUID conversationId;
  private UUID accountId;
  private Instant joinedAt;
}
