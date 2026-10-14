package com.msspoker.competitionservice.entity;

import com.msspoker.competitionservice.enums.RoomStatus;
import com.msspoker.competitionservice.enums.RoomVisibility;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "custom_rooms")
@Getter
@Setter
public class CustomRoomEntity {
    @Id private UUID id;
    private String roomCode;
    private UUID ownerId;
    private int maxPlayers;
    private long smallBlind;
    private long bigBlind;
    @Enumerated(EnumType.STRING) private RoomVisibility visibility;
    private String passwordHash;
    @Enumerated(EnumType.STRING) private RoomStatus status;
    /** CreateTable sourceId of the current or pending match; a new one is issued for every start. */
    private UUID currentSourceId;
    private UUID tableId;
    private Instant createdAt;
    private boolean isDeleted;
}
