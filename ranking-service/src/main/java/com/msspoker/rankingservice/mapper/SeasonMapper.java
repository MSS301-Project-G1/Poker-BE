package com.msspoker.rankingservice.mapper;

import com.msspoker.rankingservice.dto.request.CreateSeasonRequest;
import com.msspoker.rankingservice.dto.request.ShopGrantRequest;
import com.msspoker.rankingservice.dto.request.TierRewardRequest;
import com.msspoker.rankingservice.dto.request.WalletCreditRequest;
import com.msspoker.rankingservice.dto.response.SeasonDetailResponse;
import com.msspoker.rankingservice.dto.response.SeasonRewardResponse;
import com.msspoker.rankingservice.dto.response.TierRewardResponse;
import com.msspoker.rankingservice.entity.RewardGrantEntity;
import com.msspoker.rankingservice.entity.SeasonEntity;
import com.msspoker.rankingservice.entity.SeasonRewardClaimEntity;
import com.msspoker.rankingservice.entity.SeasonTierRewardEntity;
import com.msspoker.rankingservice.enums.RewardType;
import com.msspoker.rankingservice.enums.SeasonStatus;
import com.msspoker.rankingservice.enums.Tier;
import com.msspoker.rankingservice.util.RankingIds;

import org.mapstruct.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring", imports = {RankingIds.class, SeasonStatus.class})
public interface SeasonMapper {
    @Mapping(target = "id", expression = "java(RankingIds.next())")
    @Mapping(target = "status", expression = "java(SeasonStatus.UPCOMING)")
    @Mapping(target = "deleted", ignore = true)
    SeasonEntity newSeason(CreateSeasonRequest request);

    @Mapping(target = "id", expression = "java(RankingIds.next())")
    SeasonTierRewardEntity newReward(TierRewardRequest request, UUID seasonId);

    @Mapping(target = "id", expression = "java(RankingIds.next())")
    SeasonRewardClaimEntity newClaim(UUID seasonId, UUID accountId, Tier tier, Instant claimedAt);

    @Mapping(target = "id", expression = "java(RankingIds.next())")
    @Mapping(target = "grantedAt", ignore = true)
    @Mapping(target = "attempts", ignore = true)
    RewardGrantEntity newGrant(UUID accountId, RewardType rewardType, long amount, UUID productId, String reason,
                               String refId, String idempotencyKey, Instant createdAt);

    WalletCreditRequest credit(RewardGrantEntity grant);

    ShopGrantRequest item(RewardGrantEntity grant);

    TierRewardResponse reward(SeasonTierRewardEntity reward);

    SeasonDetailResponse detail(SeasonEntity season, List<TierRewardResponse> rewards);

    @Mapping(target = "seasonId", source = "season.id")
    @Mapping(target = "seasonName", source = "season.name")
    @Mapping(target = "endAt", source = "season.endAt")
    SeasonRewardResponse claimable(SeasonEntity season, Tier finalTier, List<TierRewardResponse> rewards,
                                   boolean claimed, Instant claimedAt);
}
