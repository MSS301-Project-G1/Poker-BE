package com.msspoker.chatservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "match_lifecycle")
@Getter
@NoArgsConstructor
public class MatchLifecycleEntity {
    @Id
    @Column(name = "match_id")
    private UUID matchId;

    @Column(name = "finished_at")
    private Instant finishedAt;

    public void finish(Instant at) {
        if (finishedAt == null) finishedAt = at;
    }
}
