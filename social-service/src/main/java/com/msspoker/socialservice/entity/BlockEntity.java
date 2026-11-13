package com.msspoker.socialservice.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "blocks")
@Getter
@Setter
@NoArgsConstructor
public class BlockEntity {
    @Id private UUID id;
    private UUID blockerId;
    private UUID blockedId;
    private Instant createdAt;
    private Instant updatedAt;
    private boolean isDeleted;
}
