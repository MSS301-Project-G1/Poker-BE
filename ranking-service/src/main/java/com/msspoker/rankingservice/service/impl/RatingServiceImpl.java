package com.msspoker.rankingservice.service.impl;

import com.msspoker.common.event.AccountRegisteredEvent;
import com.msspoker.rankingservice.config.RankingProperties;
import com.msspoker.rankingservice.dto.event.MatchFinishedEvent;
import com.msspoker.rankingservice.dto.event.PlacementEvent;
import com.msspoker.rankingservice.entity.PlayerEloEntity;
import com.msspoker.rankingservice.enums.GameMode;
import com.msspoker.rankingservice.exception.RankingExceptions;
import com.msspoker.rankingservice.mapper.RankingMapper;
import com.msspoker.rankingservice.repository.EloHistoryRepository;
import com.msspoker.rankingservice.repository.PlayerEloRepository;
import com.msspoker.rankingservice.repository.RewardCreditRepository;
import com.msspoker.rankingservice.service.EloCalculator;
import com.msspoker.rankingservice.service.RatingService;
import com.msspoker.rankingservice.service.SeasonService;
import com.msspoker.rankingservice.validator.MatchResultValidator;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RatingServiceImpl implements RatingService {
    private final SeasonService seasons;
    private final PlayerEloRepository players;
    private final EloHistoryRepository history;
    private final RewardCreditRepository rewards;
    private final EloCalculator calculator;
    private final MatchResultValidator validator;
    private final RankingMapper mapper;
    private final RankingProperties properties;

    /** Idempotent: a redelivered event finds the existing season record. */
    @Transactional
    @Override
    public void accountRegistered(AccountRegisteredEvent event) {
        if (event == null || event.accountId() == null) throw RankingExceptions.invalidEvent();
        player(seasons.lockActive().getId(), event.accountId());
    }

    /** Only RANK changes Elo. Elo, history and pending rewards commit together; elo_history dedupes the match. */
    @Transactional
    @Override
    public void matchFinished(MatchFinishedEvent event) {
        validator.validate(event);
        if (event.mode() != GameMode.RANK || history.existsByMatchId(event.matchId())) return;
        UUID seasonId = seasons.lockActive().getId();
        Instant now = Instant.now();
        for (PlacementEvent placement : event.placements()) {
            PlayerEloEntity player = player(seasonId, placement.accountId());
            int before = player.getCurrentElo();
            player.setCurrentElo(calculator.apply(before, calculator.delta(placement.place())));
            player.setPeakElo(Math.max(player.getPeakElo(), player.getCurrentElo()));
            player.setTier(calculator.tier(player.getCurrentElo()));
            if (calculator.extendsWinStreak(placement.place())) {
                player.setWinStreak(player.getWinStreak() + 1);
                player.setLossStreak(0);
            } else {
                player.setLossStreak(player.getLossStreak() + 1);
                player.setWinStreak(0);
            }
            player.setMatchesPlayed(player.getMatchesPlayed() + 1);
            player.setLastMatchAt(event.finishedAt() == null ? now : event.finishedAt());
            // Record the applied change, which differs from the table when the Elo floor is reached.
            history.save(mapper.history(player, placement, event.matchId(), player.getCurrentElo() - before, now));
            long amount = calculator.reward(placement.place(), event.entryFee());
            if (amount > 0) rewards.save(mapper.reward(placement, event.matchId(), amount, now));
        }
    }

    private PlayerEloEntity player(UUID seasonId, UUID accountId) {
        return players.findBySeasonIdAndAccountIdAndIsDeletedFalse(seasonId, accountId)
                .orElseGet(() -> players.save(mapper.player(seasonId, accountId, properties.getStartingElo(),
                        calculator.tier(properties.getStartingElo()))));
    }
}
