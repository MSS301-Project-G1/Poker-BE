package com.msspoker.rankingservice.service;

import com.msspoker.rankingservice.dto.request.CreateSeasonRequest;
import com.msspoker.rankingservice.dto.request.TierRewardRequest;
import com.msspoker.rankingservice.entity.SeasonEntity;
import com.msspoker.rankingservice.entity.SeasonTierRewardEntity;
import com.msspoker.rankingservice.enums.RewardType;
import com.msspoker.rankingservice.enums.SeasonStatus;
import com.msspoker.rankingservice.enums.Tier;
import com.msspoker.rankingservice.repository.*;
import com.msspoker.rankingservice.util.RankingIds;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/** Season tests close seasons, so they use their own H2 database and restore the seed before each test. */
public final class SeasonTestSupport {
    public static final String DATABASE = "spring.datasource.url=jdbc:h2:mem:ranking-seasons;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
    public static final UUID SEED_SEASON = UUID.fromString("0199e0a0-0000-7000-8000-000000000001");
    static final List<Object[]> SEED_REWARDS = List.of(
            new Object[]{Tier.IRON, 50L}, new Object[]{Tier.BRONZE, 100L}, new Object[]{Tier.SILVER, 150L},
            new Object[]{Tier.GOLD, 200L}, new Object[]{Tier.PLATINUM, 300L}, new Object[]{Tier.DIAMOND, 400L},
            new Object[]{Tier.CHAMPION, 500L}, new Object[]{Tier.GRAND_CHAMPION, 700L}, new Object[]{Tier.CARD_GOD, 1000L});

    private SeasonTestSupport() {
    }

    public static void reset(SeasonRepository seasons, SeasonTierRewardRepository rewards, SeasonRewardClaimRepository claims,
                             RewardGrantRepository grants, PlayerEloRepository players, EloHistoryRepository history,
                             RewardCreditRepository credits, OutboxRepository outbox) {
        grants.deleteAll();
        claims.deleteAll();
        outbox.deleteAll();
        credits.deleteAll();
        history.deleteAll();
        players.deleteAll();
        rewards.deleteAll();
        seasons.findAll().stream().filter(season -> !season.getId().equals(SEED_SEASON)).forEach(seasons::delete);
        SeasonEntity seed = seasons.findById(SEED_SEASON).orElseThrow();
        seed.setStatus(SeasonStatus.ACTIVE);
        seed.setStartAt(Instant.parse("2026-09-30T17:00:00Z"));
        seed.setEndAt(Instant.parse("2026-12-31T17:00:00Z"));
        seed.setResetPercent(50);
        seasons.save(seed);
        rewards.saveAll(SEED_REWARDS.stream().map(row -> {
            SeasonTierRewardEntity reward = new SeasonTierRewardEntity();
            reward.setId(RankingIds.next());
            reward.setSeasonId(SEED_SEASON);
            reward.setTier((Tier) row[0]);
            reward.setRewardType(RewardType.COIN);
            reward.setAmount((Long) row[1]);
            return reward;
        }).toList());
    }

    public static CreateSeasonRequest nextSeason(List<TierRewardRequest> rewards) {
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        return new CreateSeasonRequest("Mùa 2", start, start.plus(90, ChronoUnit.DAYS), 50, rewards);
    }
}
