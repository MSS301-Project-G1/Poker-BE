package com.msspoker.chatservice.entity;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chat_locks")
@Getter
@NoArgsConstructor
public class ChatLock {
  @Id
  @Column(name = "lock_key")
  private String lockKey;
}
