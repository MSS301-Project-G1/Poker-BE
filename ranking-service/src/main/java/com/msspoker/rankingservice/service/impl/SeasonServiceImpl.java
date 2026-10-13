package com.msspoker.rankingservice.service.impl;

import com.msspoker.rankingservice.constant.RankingConstants;
import com.msspoker.rankingservice.dto.request.CreateSeasonRequest;
import com.msspoker.rankingservice.dto.request.TierRewardRequest;
import com.msspoker.rankingservice.dto.request.UpdateSeasonRewardsRequest;
import com.msspoker.rankingservice.dto.response.SeasonDetailResponse;
import com.msspoker.rankingservice.dto.response.SeasonResponse;
import com.msspoker.rankingservice.dto.response.TierRewardResponse;
import com.msspoker.rankingservice.entity.PlayerEloEntity;
import com.msspoker.rankingservice.entity.SeasonEntity;
import com.msspoker.rankingservice.entity.SeasonTierRewardEntity;
import com.msspoker.rankingservice.enums.NotificationType;
import com.msspoker.rankingservice.enums.SeasonStatus;
import com.msspoker.rankingservice.enums.Tier;
import com.msspoker.rankingservice.exception.RankingExceptions;
import com.msspoker.rankingservice.mapper.RankingMapper;
import com.msspoker.rankingservice.mapper.SeasonMapper;
import com.msspoker.rankingservice.repository.PlayerEloRepository;
import com.msspoker.rankingservice.repository.SeasonRepository;
import com.msspoker.rankingservice.repository.SeasonTierRewardRepository;
import com.msspoker.rankingservice.service.EloCalculator;
import com.msspoker.rankingservice.service.NotificationService;
import com.msspoker.rankingservice.service.SeasonService;
import com.msspoker.rankingservice.validator.TierRewardsValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeasonServiceImpl implements SeasonService {
    private static final Comparator<TierRewardResponse> BY_TIER = Comparator.comparing(TierRewardResponse::tier)
            .thenComparing(TierRewardResponse::rewardType);

    private final SeasonRepository repository;
    private final SeasonTierRewardRepository tierRewards;
    private final PlayerEloRepository players;
    private final EloCalculator calculator;
    private final NotificationService notifications;
    private final TierRewardsValidator rewardsValidator;
    private final RankingMapper mapper;
    private final SeasonMapper seasonMapper;
    @Value("${ranking.seasons.notification-batch:500}") private int notificationBatch;

    @Transactional(readOnly = true)
    @Override
    public SeasonEntity active() {
        return repository.findFirstByStatusAndIsDeletedFalse(SeasonStatus.ACTIVE)
                .orElseThrow(RankingExceptions::noActiveSeason);
    }

    @Transactional
    @Override
    public SeasonEntity lockActive() {
        return repository.lockByStatus(SeasonStatus.ACTIVE).stream().findFirst()
                .orElseThrow(RankingExceptions::noActiveSeason);
    }

    @Transactional(readOnly = true)
    @Override
    public SeasonResponse current() {
        return mapper.season(active());
    }

    @Transactional(readOnly = true)
    @Override
    public List<TierRewardResponse> rewards(UUID seasonId) {
        find(seasonId);
        return rewardViews(tierRewards.findBySeasonId(seasonId));
    }

    @Transactional(readOnly = true)
    @Override
    public List<SeasonDetailResponse> list() {
        List<SeasonEntity> seasons = repository.findByIsDeletedFalseOrderByStartAtDescIdDesc();
        Map<UUID, List<SeasonTierRewardEntity>> rewards = tierRewards.findBySeasonIdIn(
                seasons.stream().map(SeasonEntity::getId).toList()).stream()
                .collect(Collectors.groupingBy(SeasonTierRewardEntity::getSeasonId));
        return seasons.stream().map(season -> seasonMapper.detail(season,
                rewardViews(rewards.getOrDefault(season.getId(), List.of())))).toList();
    }

    @Transactional
    @Override
    public SeasonDetailResponse create(CreateSeasonRequest request) {
        rewardsValidator.validate(request.rewards());
        SeasonEntity season = repository.save(seasonMapper.newSeason(request));
        return seasonMapper.detail(season, saveRewards(season.getId(), request.rewards()));
    }

    @Transactional
    @Override
    public SeasonDetailResponse updateRewards(UUID seasonId, UpdateSeasonRewardsRequest request) {
        SeasonEntity season = find(seasonId);
        // Rewards of an ended season are what players claim; they cannot change afterwards.
        if (season.getStatus() == SeasonStatus.ENDED) throw RankingExceptions.seasonRewardsLocked();
        rewardsValidator.validate(request.rewards());
        tierRewards.deleteBySeasonId(seasonId);
        tierRewards.flush();
        return seasonMapper.detail(season, saveRewards(seasonId, request.rewards()));
    }

    /**
     * Snapshots final tiers in leaderboard order (CARD_GOD needs the position), carries the kept Elo
     * into the next season and announces rewards. Matches finishing now wait on the season lock.
     */
    @Transactional
    @Override
    public SeasonDetailResponse close() {
        SeasonEntity ending = lockActive();
        SeasonEntity next = repository.findFirstByStatusAndIsDeletedFalseOrderByStartAtAscIdAsc(SeasonStatus.UPCOMING)
                .orElseThrow(RankingExceptions::noNextSeason);
        Instant now = Instant.now();
        List<PlayerEloEntity> standings = players.findBySeasonIdAndIsDeletedFalse(ending.getId(),
                RankingConstants.LEADERBOARD_SORT);
        List<PlayerEloEntity> carried = new ArrayList<>();
        for (int i = 0; i < standings.size(); i++) {
            PlayerEloEntity player = standings.get(i);
            player.setFinalTier(calculator.displayTier(player.getTier(), i + 1));
            int elo = calculator.carryOver(player.getCurrentElo(), ending.getResetPercent());
            carried.add(mapper.player(next.getId(), player.getAccountId(), elo, calculator.tier(elo)));
        }
        players.saveAll(carried);
        if (now.isBefore(ending.getEndAt())) ending.setEndAt(now);
        ending.setStatus(SeasonStatus.ENDED);
        if (next.getStartAt().isAfter(now)) next.setStartAt(now);
        next.setStatus(SeasonStatus.ACTIVE);
        announceRewards(ending, standings);
        log.info("Season {} closed for {} players; season {} is active", ending.getName(), standings.size(), next.getName());
        return seasonMapper.detail(ending, rewardViews(tierRewards.findBySeasonId(ending.getId())));
    }

    @Scheduled(fixedDelayString = "${ranking.seasons.check-millis:60000}")
    @Transactional
    @Override
    public void closeDueSeason() {
        Instant now = Instant.now();
        Optional<SeasonEntity> next = repository.findFirstByStatusAndIsDeletedFalseOrderByStartAtAscIdAsc(SeasonStatus.UPCOMING);
        Optional<SeasonEntity> active = repository.findFirstByStatusAndIsDeletedFalse(SeasonStatus.ACTIVE);
        if (active.isEmpty()) {
            next.filter(season -> !season.getStartAt().isAfter(now)).ifPresent(season -> season.setStatus(SeasonStatus.ACTIVE));
            return;
        }
        // Without a next season the current one keeps running, so players are never left without a season.
        if (!active.get().getEndAt().isAfter(now) && next.isPresent()) close();
    }

    private void announceRewards(SeasonEntity ending, List<PlayerEloEntity> standings) {
        Optional<Tier> lowestRewarded = tierRewards.findBySeasonId(ending.getId()).stream()
                .map(SeasonTierRewardEntity::getTier).min(Comparator.naturalOrder());
        if (lowestRewarded.isEmpty()) return;
        List<UUID> eligible = standings.stream().filter(player -> player.getFinalTier().compareTo(lowestRewarded.get()) >= 0)
                .map(PlayerEloEntity::getAccountId).toList();
        for (int from = 0; from < eligible.size(); from += notificationBatch) {
            notifications.notify(NotificationType.SEASON_REWARD_AVAILABLE,
                    eligible.subList(from, Math.min(eligible.size(), from + notificationBatch)),
                    "Quà mùa giải đã sẵn sàng", ending.getName() + " đã kết thúc. Nhận quà theo bậc của bạn.",
                    Map.of("seasonId", ending.getId(), "seasonName", ending.getName()));
        }
    }

    private List<TierRewardResponse> saveRewards(UUID seasonId, List<TierRewardRequest> rewards) {
        if (rewards == null || rewards.isEmpty()) return List.of();
        return rewardViews(tierRewards.saveAll(rewards.stream().map(reward -> seasonMapper.newReward(reward, seasonId)).toList()));
    }

    private List<TierRewardResponse> rewardViews(List<SeasonTierRewardEntity> rewards) {
        return rewards.stream().map(seasonMapper::reward).sorted(BY_TIER).toList();
    }

    private SeasonEntity find(UUID seasonId) {
        return repository.findByIdAndIsDeletedFalse(seasonId).orElseThrow(RankingExceptions::seasonNotFound);
    }
}
