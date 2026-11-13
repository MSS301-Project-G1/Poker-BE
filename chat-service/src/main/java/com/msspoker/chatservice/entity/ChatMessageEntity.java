package com.msspoker.chatservice.entity;

import com.msspoker.chatservice.enums.MessageType;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
public class ChatMessageEntity {
    @Id private UUID id;
    private UUID conversationId;
    private UUID senderId;

    @Enumerated(EnumType.STRING)
    private MessageType messageType;

    private String content;
    private UUID clientMessageId;
    private Instant createdAt;
}
