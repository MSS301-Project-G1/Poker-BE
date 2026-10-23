package com.msspoker.chatservice.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
public class ChatMessage {
  @Id private UUID id;
  private UUID conversationId;
  private UUID senderId;
  private String messageType;
  private String content;
  private UUID clientMessageId;
  private Instant createdAt;
}
