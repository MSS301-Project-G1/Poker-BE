package com.msspoker.rankingservice.entity;

import com.msspoker.rankingservice.enums.SeasonStatus;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "seasons")
@Getter
@Setter
public class SeasonEntity {
    @Id private UUID id;
    private String name;
    private Instant startAt;
    private Instant endAt;
    @Enumerated(EnumType.STRING) private SeasonStatus status;
    private int resetPercent;
    private boolean isDeleted;
}
