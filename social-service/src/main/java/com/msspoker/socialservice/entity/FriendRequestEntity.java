package com.msspoker.socialservice.entity;

import com.msspoker.socialservice.enums.FriendRequestStatus;

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
@Table(name = "friend_requests")
@Getter
@Setter
@NoArgsConstructor
public class FriendRequestEntity {
    @Id private UUID id;
    private UUID requesterId;
    private UUID addresseeId;
    private String pairKey;

    @Enumerated(EnumType.STRING)
    private FriendRequestStatus status;

    private Instant createdAt;
    private Instant updatedAt;
    private boolean isDeleted;
}
