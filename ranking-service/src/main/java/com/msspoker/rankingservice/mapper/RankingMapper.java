package com.msspoker.rankingservice.mapper;

import com.msspoker.rankingservice.constant.RankingConstants;
import com.msspoker.rankingservice.dto.event.PlacementEvent;
import com.msspoker.rankingservice.dto.request.WalletCreditRequest;
import com.msspoker.rankingservice.dto.response.FriendRankingResponse;
import com.msspoker.rankingservice.dto.response.MatchRatingResponse;
import com.msspoker.rankingservice.dto.response.MyRankingResponse;
import com.msspoker.rankingservice.dto.response.PlayerRankingResponse;
import com.msspoker.rankingservice.dto.response.RankedPlayerResponse;
import com.msspoker.rankingservice.dto.response.SeasonResponse;
import com.msspoker.rankingservice.entity.EloHistoryEntity;
import com.msspoker.rankingservice.entity.PlayerEloEntity;
import com.msspoker.rankingservice.entity.RewardCreditEntity;
import com.msspoker.rankingservice.entity.SeasonEntity;
import com.msspoker.rankingservice.enums.Tier;
import com.msspoker.rankingservice.util.RankingIds;

import org.mapstruct.*;

import java.time.Instant;
import java.util.UUID;

@Mapper(componentModel = "spring", imports = {RankingIds.class, RankingConstants.class})
public interface RankingMapper {
    SeasonResponse season(SeasonEntity entity);

    @Mapping(target = "id", expression = "java(RankingIds.next())")
    @Mapping(target = "currentElo", source = "elo")
    @Mapping(target = "peakElo", source = "elo")
    @Mapping(target = "winStreak", ignore = true)
    @Mapping(target = "lossStreak", ignore = true)
    @Mapping(target = "matchesPlayed", ignore = true)
    @Mapping(target = "lastMatchAt", ignore = true)
    @Mapping(target = "finalTier", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    PlayerEloEntity player(UUID seasonId, UUID accountId, int elo, Tier tier);

    @Mapping(target = "id", expression = "java(RankingIds.next())")
    @Mapping(target = "accountId", source = "player.accountId")
    @Mapping(target = "seasonId", source = "player.seasonId")
    @Mapping(target = "eloAfter", source = "player.currentElo")
    @Mapping(target = "place", source = "placement.place")
    EloHistoryEntity history(PlayerEloEntity player, PlacementEvent placement, UUID matchId, int delta, Instant createdAt);

    @Mapping(target = "id", expression = "java(RankingIds.next())")
    @Mapping(target = "accountId", source = "placement.accountId")
    @Mapping(target = "place", source = "placement.place")
    @Mapping(target = "idempotencyKey", expression = "java(String.format(RankingConstants.RANK_REWARD_KEY_FORMAT, matchId, placement.accountId()))")
    @Mapping(target = "creditedAt", ignore = true)
    @Mapping(target = "attempts", ignore = true)
    RewardCreditEntity reward(PlacementEvent placement, UUID matchId, long amount, Instant createdAt);

    @Mapping(target = "reason", constant = RankingConstants.RANK_REWARD_REASON)
    @Mapping(target = "refId", expression = "java(reward.getMatchId().toString())")
    WalletCreditRequest credit(RewardCreditEntity reward);

    @Mapping(target = "elo", source = "player.currentElo")
    @Mapping(target = "tier", source = "tier")
    PlayerRankingResponse ranking(PlayerEloEntity player, Tier tier);

    @Mapping(target = "elo", source = "player.currentElo")
    @Mapping(target = "tier", source = "tier")
    RankedPlayerResponse ranked(PlayerEloEntity player, long position, Tier tier);

    @Mapping(target = "elo", source = "player.currentElo")
    @Mapping(target = "tier", source = "tier")
    FriendRankingResponse friend(PlayerEloEntity player, int rank, Long position, Tier tier, boolean you);

    @Mapping(target = "elo", source = "player.currentElo")
    @Mapping(target = "tier", source = "tier")
    MyRankingResponse me(PlayerEloEntity player, Long position, Tier tier);

    @Mapping(target = "eloDelta", source = "history.delta")
    MatchRatingResponse matchRating(EloHistoryEntity history, long rewardCoins);
}
