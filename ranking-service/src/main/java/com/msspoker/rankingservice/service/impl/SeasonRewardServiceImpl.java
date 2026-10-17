package com.msspoker.rankingservice.service.impl;

import com.msspoker.rankingservice.constant.RankingConstants;
import com.msspoker.rankingservice.dto.response.SeasonRewardResponse;
import com.msspoker.rankingservice.dto.response.TierRewardResponse;
import com.msspoker.rankingservice.entity.PlayerEloEntity;
import com.msspoker.rankingservice.entity.SeasonEntity;
import com.msspoker.rankingservice.entity.SeasonRewardClaimEntity;
import com.msspoker.rankingservice.entity.SeasonTierRewardEntity;
import com.msspoker.rankingservice.enums.RewardType;
import com.msspoker.rankingservice.enums.SeasonStatus;
import com.msspoker.rankingservice.enums.Tier;
import com.msspoker.rankingservice.exception.RankingExceptions;
import com.msspoker.rankingservice.mapper.SeasonMapper;
import com.msspoker.rankingservice.repository.*;
import com.msspoker.rankingservice.service.SeasonRewardService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeasonRewardServiceImpl implements SeasonRewardService {
    private final SeasonRepository seasons;
    private final SeasonTierRewardRepository tierRewards;
    private final SeasonRewardClaimRepository claims;
    private final RewardGrantRepository grants;
    private final PlayerEloRepository players;
    private final SeasonMapper mapper;

    @Transactional(readOnly = true)
    @Override
    public List<SeasonRewardResponse> mine(UUID accountId) {
        List<PlayerEloEntity> finished = players.findByAccountIdAndFinalTierIsNotNullAndIsDeletedFalse(accountId);
        if (finished.isEmpty()) return List.of();
        List<UUID> seasonIds = finished.stream().map(PlayerEloEntity::getSeasonId).toList();
        Map<UUID, SeasonEntity> byId = seasons.findAllById(seasonIds).stream()
                .collect(Collectors.toMap(SeasonEntity::getId, Function.identity()));
        Map<UUID, List<SeasonTierRewardEntity>> rewards = tierRewards.findBySeasonIdIn(seasonIds).stream()
                .collect(Collectors.groupingBy(SeasonTierRewardEntity::getSeasonId));
        Map<UUID, SeasonRewardClaimEntity> claimed = claims.findByAccountId(accountId).stream()
                .collect(Collectors.toMap(SeasonRewardClaimEntity::getSeasonId, Function.identity()));
        return finished.stream().map(player -> {
                    SeasonEntity season = byId.get(player.getSeasonId());
                    List<TierRewardResponse> earned = earned(rewards.getOrDefault(season.getId(), List.of()), player.getFinalTier());
                    SeasonRewardClaimEntity claim = claimed.get(season.getId());
                    return mapper.claimable(season, player.getFinalTier(), earned, claim != null,
                            claim == null ? null : claim.getClaimedAt());
                })
                .filter(view -> !view.rewards().isEmpty())
                .sorted(Comparator.comparing(SeasonRewardResponse::endAt).reversed())
                .toList();
    }

    /** The claim and its pending grants commit together; the grant dispatcher delivers them. */
    @Transactional
    @Override
    public SeasonRewardResponse claim(UUID seasonId, UUID accountId) {
        SeasonEntity season = seasons.findByIdAndIsDeletedFalse(seasonId).orElseThrow(RankingExceptions::seasonNotFound);
        if (season.getStatus() != SeasonStatus.ENDED) throw RankingExceptions.seasonNotEnded();
        PlayerEloEntity player = players.findBySeasonIdAndAccountIdAndIsDeletedFalse(seasonId, accountId)
                .filter(found -> found.getFinalTier() != null).orElseThrow(RankingExceptions::noSeasonReward);
        List<SeasonTierRewardEntity> eligible = tierRewards.findBySeasonId(seasonId).stream()
                .filter(reward -> reward.getTier().compareTo(player.getFinalTier()) <= 0).toList();
        if (eligible.isEmpty()) throw RankingExceptions.noSeasonReward();
        List<TierRewardResponse> earned = earned(eligible, player.getFinalTier());
        var existing = claims.findBySeasonIdAndAccountId(seasonId, accountId);
        if (existing.isPresent()) return mapper.claimable(season, player.getFinalTier(), earned, true, existing.get().getClaimedAt());

        Instant now = Instant.now();
        claims.save(mapper.newClaim(seasonId, accountId, player.getFinalTier(), now));
        for (SeasonTierRewardEntity reward : eligible) {
            boolean coin = reward.getRewardType() == RewardType.COIN;
            String key = coin
                    ? String.format(RankingConstants.SEASON_COIN_KEY_FORMAT, seasonId, reward.getTier(), accountId)
                    : String.format(RankingConstants.SEASON_ITEM_KEY_FORMAT, seasonId, reward.getProductId(), accountId);
            grants.save(mapper.newGrant(accountId, reward.getRewardType(), reward.getAmount(), reward.getProductId(),
                    RankingConstants.SEASON_REWARD_REASON, seasonId.toString(), key, now));
        }
        return mapper.claimable(season, player.getFinalTier(), earned, true, now);
    }

    private List<TierRewardResponse> earned(List<SeasonTierRewardEntity> rewards, Tier finalTier) {
        return rewards.stream().filter(reward -> reward.getTier().compareTo(finalTier) <= 0).map(mapper::reward)
                .sorted(Comparator.comparing(TierRewardResponse::tier).thenComparing(TierRewardResponse::rewardType)).toList();
    }
}
