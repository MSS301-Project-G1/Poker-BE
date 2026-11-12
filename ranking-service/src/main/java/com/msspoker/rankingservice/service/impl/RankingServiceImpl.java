package com.msspoker.rankingservice.service.impl;

import com.msspoker.rankingservice.config.RankingProperties;
import com.msspoker.rankingservice.constant.RankingConstants;
import com.msspoker.rankingservice.dto.response.FriendRankingResponse;
import com.msspoker.rankingservice.dto.response.MatchRatingResponse;
import com.msspoker.rankingservice.dto.response.MyRankingResponse;
import com.msspoker.rankingservice.dto.response.PageResponse;
import com.msspoker.rankingservice.dto.response.PlayerRankingResponse;
import com.msspoker.rankingservice.dto.response.RankedPlayerResponse;
import com.msspoker.rankingservice.entity.EloHistoryEntity;
import com.msspoker.rankingservice.entity.PlayerEloEntity;
import com.msspoker.rankingservice.entity.RewardCreditEntity;
import com.msspoker.rankingservice.enums.Tier;
import com.msspoker.rankingservice.exception.RankingExceptions;
import com.msspoker.rankingservice.mapper.RankingMapper;
import com.msspoker.rankingservice.repository.EloHistoryRepository;
import com.msspoker.rankingservice.repository.PlayerEloRepository;
import com.msspoker.rankingservice.repository.RewardCreditRepository;
import com.msspoker.rankingservice.service.AdminAccountService;
import com.msspoker.rankingservice.service.EloCalculator;
import com.msspoker.rankingservice.service.RankingService;
import com.msspoker.rankingservice.service.SeasonService;
import com.msspoker.rankingservice.service.client.SocialClient;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RankingServiceImpl implements RankingService {
    private final SeasonService seasons;
    private final PlayerEloRepository players;
    private final EloHistoryRepository history;
    private final RewardCreditRepository rewards;
    private final EloCalculator calculator;
    private final RankingMapper mapper;
    private final RankingProperties properties;
    private final SocialClient social;
    private final AdminAccountService adminAccounts;
    private final PlatformTransactionManager transactionManager;

    /**
     * Accounts without a record yet report the starting Elo, so callers never fail on new players.
     * Admins are off the leaderboard, so they never hold a Card God place.
     */
    @Transactional(readOnly = true)
    @Override
    public PlayerRankingResponse ranking(UUID accountId) {
        UUID seasonId = seasons.active().getId();
        Set<UUID> admins = adminAccounts.adminIds();
        PlayerEloEntity player = players.findBySeasonIdAndAccountIdAndIsDeletedFalse(seasonId, accountId)
                .orElseGet(() -> unranked(seasonId, accountId));
        Tier tier = player.getTier() == Tier.GRAND_CHAMPION && !admins.contains(accountId)
                ? calculator.displayTier(player.getTier(), position(player, excluded(admins))) : player.getTier();
        return mapper.ranking(player, tier);
    }

    @Transactional(readOnly = true)
    @Override
    public List<RankedPlayerResponse> top(int limit) {
        return board(0, limit).content();
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<RankedPlayerResponse> leaderboard(int page, int size) {
        return board(page, size);
    }

    @Transactional(readOnly = true)
    @Override
    public MyRankingResponse me(UUID accountId) {
        UUID seasonId = seasons.active().getId();
        Set<UUID> admins = adminAccounts.adminIds();
        Optional<PlayerEloEntity> found = players.findBySeasonIdAndAccountIdAndIsDeletedFalse(seasonId, accountId);
        // An admin keeps their Elo but, like a player without a record, has no position.
        if (found.isEmpty() || admins.contains(accountId)) {
            PlayerEloEntity player = found.orElseGet(() -> unranked(seasonId, accountId));
            return mapper.me(player, null, player.getTier());
        }
        long position = position(found.get(), excluded(admins));
        return mapper.me(found.get(), position, calculator.displayTier(found.get().getTier(), position));
    }

    /**
     * Friends come from social-service before any database work, so a slow social call holds no
     * connection. Records and positions are read from one snapshot, so the order always matches
     * the positions shown. Admin friends are left out; an admin caller still sees their own row.
     */
    @Override
    public List<FriendRankingResponse> friends(UUID accountId) {
        Set<UUID> admins = adminAccounts.adminIds();
        Set<UUID> circle = new LinkedHashSet<>();
        circle.add(accountId);
        social.friends(accountId).stream().filter(friend -> !admins.contains(friend)).forEach(circle::add);
        List<UUID> members = circle.stream().limit(RankingConstants.MAX_FRIENDS).toList();
        TransactionTemplate snapshot = new TransactionTemplate(transactionManager);
        snapshot.setReadOnly(true);
        snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return snapshot.execute(status -> friendsBoard(accountId, members, excluded(admins)));
    }

    /** The reward row is written in the same transaction as the Elo history, so a missing row means no reward. */
    @Transactional(readOnly = true)
    @Override
    public MatchRatingResponse matchRating(UUID accountId, UUID matchId) {
        EloHistoryEntity result = history.findByMatchIdAndAccountId(matchId, accountId)
                .orElseThrow(RankingExceptions::matchRatingNotFound);
        long rewardCoins = rewards.findByMatchIdAndAccountId(matchId, accountId)
                .map(RewardCreditEntity::getAmount).orElse(0L);
        return mapper.matchRating(result, rewardCoins);
    }

    private List<FriendRankingResponse> friendsBoard(UUID accountId, List<UUID> members, Collection<UUID> excluded) {
        UUID seasonId = seasons.active().getId();
        Map<UUID, PlayerEloEntity> records = players.findBySeasonIdAndAccountIdInAndIsDeletedFalse(seasonId, members)
                .stream().collect(Collectors.toMap(PlayerEloEntity::getAccountId, Function.identity()));
        Map<UUID, Long> positions = players.positions(seasonId, members, excluded).stream()
                .collect(Collectors.toMap(row -> UUID.fromString(row.getAccountId()), PlayerEloRepository.Position::getPosition));
        // Ranked players by season position, then players without one (no record, or an admin caller) in id
        // order (UUID text order, as in SQL).
        List<UUID> order = members.stream().sorted(Comparator
                        .comparing((UUID member) -> positions.getOrDefault(member, Long.MAX_VALUE))
                        .thenComparing(UUID::toString))
                .toList();
        List<FriendRankingResponse> board = new ArrayList<>();
        for (UUID member : order) {
            Long position = positions.get(member);
            PlayerEloEntity player = records.containsKey(member) ? records.get(member) : unranked(seasonId, member);
            Tier tier = position == null ? player.getTier() : calculator.displayTier(player.getTier(), position);
            board.add(mapper.friend(player, board.size() + 1, position, tier, member.equals(accountId)));
        }
        return board;
    }

    private PageResponse<RankedPlayerResponse> board(int page, int size) {
        Page<PlayerEloEntity> result = players.findBySeasonIdAndAccountIdNotInAndIsDeletedFalse(seasons.active().getId(),
                excluded(adminAccounts.adminIds()), PageRequest.of(page, size, RankingConstants.LEADERBOARD_SORT));
        List<RankedPlayerResponse> content = new ArrayList<>();
        for (int i = 0; i < result.getNumberOfElements(); i++) {
            PlayerEloEntity player = result.getContent().get(i);
            long position = (long) page * size + i + 1;
            content.add(mapper.ranked(player, position, calculator.displayTier(player.getTier(), position)));
        }
        return new PageResponse<>(content, page, size, result.getTotalElements(), result.getTotalPages());
    }

    private long position(PlayerEloEntity player, Collection<UUID> excluded) {
        return players.countAhead(player.getSeasonId(), player.getCurrentElo(), player.getAccountId(), excluded) + 1;
    }

    /** Accounts kept off the leaderboards, as a NOT IN list that is never empty. */
    private Collection<UUID> excluded(Set<UUID> admins) {
        return admins.isEmpty() ? List.of(RankingConstants.NO_ACCOUNT) : admins;
    }

    private PlayerEloEntity unranked(UUID seasonId, UUID accountId) {
        return mapper.player(seasonId, accountId, properties.getStartingElo(), calculator.tier(properties.getStartingElo()));
    }
}
