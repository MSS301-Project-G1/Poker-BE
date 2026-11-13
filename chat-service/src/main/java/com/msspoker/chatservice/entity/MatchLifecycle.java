package com.msspoker.chatservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "match_lifecycle")
@Getter
@NoArgsConstructor
public class MatchLifecycle {
  @Id
  @Column(name = "match_id")
  private UUID matchId;

  @Column(name = "finished_at")
  private Instant finishedAt;

  public void finish(Instant at) {
    if (finishedAt == null) finishedAt = at;
  }
}
