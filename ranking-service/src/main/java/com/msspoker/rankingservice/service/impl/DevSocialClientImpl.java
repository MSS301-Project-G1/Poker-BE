package com.msspoker.rankingservice.service.impl;

import com.msspoker.rankingservice.constant.RankingConstants;
import com.msspoker.rankingservice.entity.PlayerEloEntity;
import com.msspoker.rankingservice.repository.PlayerEloRepository;
import com.msspoker.rankingservice.service.SeasonService;
import com.msspoker.rankingservice.service.SocialClient;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** SPEC §9.4 mock while social-service is unavailable locally: the season's top players are everyone's friends. */
@Component
@Profile("dev | test")
@RequiredArgsConstructor
public class DevSocialClientImpl implements SocialClient {
    private final SeasonService seasons;
    private final PlayerEloRepository players;

    @Override
    public List<UUID> friends(UUID accountId) {
        return players.findBySeasonIdAndIsDeletedFalse(seasons.active().getId(),
                        PageRequest.of(0, RankingConstants.MAX_FRIENDS, RankingConstants.LEADERBOARD_SORT))
                .getContent().stream().map(PlayerEloEntity::getAccountId)
                .filter(friend -> !friend.equals(accountId)).toList();
    }
}
