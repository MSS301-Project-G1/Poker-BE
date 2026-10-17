package com.msspoker.rankingservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** rewardCoins is the reward granted at completion, so a later config change does not rewrite history. */
@Entity
@Table(name = "tutorial_completions")
@Getter
@Setter
public class TutorialCompletionEntity {
    @Id private UUID accountId;
    private long rewardCoins;
    private Instant completedAt;
}
