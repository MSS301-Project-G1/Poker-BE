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
@Table(name = "party_invitations")
@Getter
@Setter
@NoArgsConstructor
public class PartyInvitationEntity {
    @Id private UUID id;
    private UUID partyId;
    private UUID inviterId;
    private UUID recipientId;
    private String passwordHash;
    private String status;
    private Instant createdAt;
    private Instant expiresAt;
}
