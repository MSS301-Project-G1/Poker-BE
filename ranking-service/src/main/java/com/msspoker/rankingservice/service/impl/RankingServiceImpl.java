package com.msspoker.rankingservice.service.impl;

import com.msspoker.rankingservice.config.RankingProperties;
import com.msspoker.rankingservice.constant.RankingConstants;
import com.msspoker.rankingservice.dto.response.MyRankingResponse;
import com.msspoker.rankingservice.dto.response.PageResponse;
import com.msspoker.rankingservice.dto.response.PlayerRankingResponse;
import com.msspoker.rankingservice.dto.response.RankedPlayerResponse;
import com.msspoker.rankingservice.entity.PlayerEloEntity;
import com.msspoker.rankingservice.enums.Tier;
import com.msspoker.rankingservice.mapper.RankingMapper;
import com.msspoker.rankingservice.repository.PlayerEloRepository;
import com.msspoker.rankingservice.service.EloCalculator;
import com.msspoker.rankingservice.service.RankingService;
import com.msspoker.rankingservice.service.SeasonService;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RankingServiceImpl implements RankingService {
    private final SeasonService seasons;
    private final PlayerEloRepository players;
    private final EloCalculator calculator;
    private final RankingMapper mapper;
    private final RankingProperties properties;

    /** Accounts without a record yet report the starting Elo, so callers never fail on new players. */
    @Transactional(readOnly = true)
    @Override
    public PlayerRankingResponse ranking(UUID accountId) {
        UUID seasonId = seasons.active().getId();
        PlayerEloEntity player = players.findBySeasonIdAndAccountIdAndIsDeletedFalse(seasonId, accountId)
                .orElseGet(() -> unranked(seasonId, accountId));
        Tier tier = player.getTier() == Tier.GRAND_CHAMPION
                ? calculator.displayTier(player.getTier(), position(player)) : player.getTier();
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
        Optional<PlayerEloEntity> found = players.findBySeasonIdAndAccountIdAndIsDeletedFalse(seasonId, accountId);
        if (found.isEmpty()) {
            PlayerEloEntity player = unranked(seasonId, accountId);
            return mapper.me(player, null, player.getTier());
        }
        long position = position(found.get());
        return mapper.me(found.get(), position, calculator.displayTier(found.get().getTier(), position));
    }

    private PageResponse<RankedPlayerResponse> board(int page, int size) {
        Page<PlayerEloEntity> result = players.findBySeasonIdAndIsDeletedFalse(seasons.active().getId(),
                PageRequest.of(page, size, RankingConstants.LEADERBOARD_SORT));
        List<RankedPlayerResponse> content = new ArrayList<>();
        for (int i = 0; i < result.getNumberOfElements(); i++) {
            PlayerEloEntity player = result.getContent().get(i);
            long position = (long) page * size + i + 1;
            content.add(mapper.ranked(player, position, calculator.displayTier(player.getTier(), position)));
        }
        return new PageResponse<>(content, page, size, result.getTotalElements(), result.getTotalPages());
    }

    private long position(PlayerEloEntity player) {
        return players.countAhead(player.getSeasonId(), player.getCurrentElo(), player.getAccountId()) + 1;
    }

    private PlayerEloEntity unranked(UUID seasonId, UUID accountId) {
        return mapper.player(seasonId, accountId, properties.getStartingElo(), calculator.tier(properties.getStartingElo()));
    }
}
