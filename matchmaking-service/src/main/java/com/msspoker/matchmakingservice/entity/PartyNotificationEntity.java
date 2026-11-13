package com.msspoker.matchmakingservice.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "party_notification_outbox")
@Getter
@Setter
@NoArgsConstructor
public class PartyNotificationEntity {
    @Id private UUID id;
    private String body;
    private Instant createdAt;
    private Instant publishedAt;
}
