package com.msspoker.competitionservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "room_members")
@Getter
@Setter
public class RoomMemberEntity {
    @Id private UUID id;
    private UUID roomId;
    private UUID accountId;
    private boolean isReady;
    private Instant joinedAt;
}
