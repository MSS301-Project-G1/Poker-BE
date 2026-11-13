package com.msspoker.chatservice.entity;

import com.msspoker.chatservice.enums.ConversationType;

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
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
public class ConversationEntity {
    @Id private UUID id;

    @Enumerated(EnumType.STRING)
    private ConversationType type;

    private UUID refId;
    private String directKey;
    private Instant closedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private boolean isDeleted;
}
