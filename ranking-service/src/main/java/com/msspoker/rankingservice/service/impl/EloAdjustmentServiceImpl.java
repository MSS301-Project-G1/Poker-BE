package com.msspoker.rankingservice.service.impl;

import com.msspoker.rankingservice.config.RankingProperties;
import com.msspoker.rankingservice.dto.request.AdjustEloRequest;
import com.msspoker.rankingservice.dto.response.EloAdjustmentResponse;
import com.msspoker.rankingservice.dto.response.MyRankingResponse;
import com.msspoker.rankingservice.dto.response.PageResponse;
import com.msspoker.rankingservice.entity.EloAdjustmentEntity;
import com.msspoker.rankingservice.entity.PlayerEloEntity;
import com.msspoker.rankingservice.exception.RankingExceptions;
import com.msspoker.rankingservice.mapper.RankingMapper;
import com.msspoker.rankingservice.repository.EloAdjustmentRepository;
import com.msspoker.rankingservice.repository.PlayerEloRepository;
import com.msspoker.rankingservice.service.AdminAccountService;
import com.msspoker.rankingservice.service.EloAdjustmentService;
import com.msspoker.rankingservice.service.EloCalculator;
import com.msspoker.rankingservice.service.RankingService;
import com.msspoker.rankingservice.service.SeasonService;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EloAdjustmentServiceImpl implements EloAdjustmentService {
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"));

    private final SeasonService seasons;
    private final PlayerEloRepository players;
    private final EloAdjustmentRepository adjustments;
    private final AdminAccountService adminAccounts;
    private final RankingService ranking;
    private final EloCalculator calculator;
    private final RankingMapper mapper;
    private final RankingProperties properties;

    /**
     * The tier follows the new Elo, the peak only rises, streaks and match counts stay. The active season row is
     * locked like a match result, so an adjustment and a finishing match never overwrite each other.
     */
    @Transactional
    @Override
    public MyRankingResponse adjust(UUID adminId, UUID accountId, AdjustEloRequest request) {
        // Admins are off the leaderboard, so no admin's Elo (the caller's included) is edited here.
        if (adminId.equals(accountId) || adminAccounts.adminIds().contains(accountId)) throw RankingExceptions.adminTarget();
        UUID seasonId = seasons.lockActive().getId();
        PlayerEloEntity player = players.findBySeasonIdAndAccountIdAndIsDeletedFalse(seasonId, accountId)
                .orElseGet(() -> mapper.player(seasonId, accountId, properties.getStartingElo(),
                        calculator.tier(properties.getStartingElo())));
        int before = player.getCurrentElo();
        if (before == request.elo()) throw RankingExceptions.eloUnchanged();
        player.setCurrentElo(request.elo());
        player.setPeakElo(Math.max(player.getPeakElo(), request.elo()));
        player.setTier(calculator.tier(request.elo()));
        // Flushed before the position query below reads the leaderboard.
        players.saveAndFlush(player);
        adjustments.save(mapper.adjustment(player, adminId, before, request.reason().strip(), Instant.now()));
        return ranking.me(accountId);
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<EloAdjustmentResponse> history(UUID accountId, int page, int size) {
        Page<EloAdjustmentEntity> result = adjustments.findByAccountId(accountId, PageRequest.of(page, size, NEWEST_FIRST));
        return new PageResponse<>(result.getContent().stream().map(mapper::adjustment).toList(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }
}
