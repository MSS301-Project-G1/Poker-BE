package com.msspoker.rankingservice.service.impl.client;

import com.msspoker.rankingservice.constant.RankingConstants;
import com.msspoker.rankingservice.entity.PlayerEloEntity;
import com.msspoker.rankingservice.exception.RankingExceptions;
import com.msspoker.rankingservice.repository.PlayerEloRepository;
import com.msspoker.rankingservice.service.SeasonService;
import com.msspoker.rankingservice.service.client.SocialClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * With ranking.social.mock (dev and test, SPEC §9.4) social-service is not running, so the active
 * season's top players are everyone's friends.
 */
@Component
public class SocialClientImpl implements SocialClient {
    private final RestClient social;
    private final boolean mock;
    private final SeasonService seasons;
    private final PlayerEloRepository players;

    public SocialClientImpl(@Value("${ranking.social-url:http://localhost:8084}") String url,
            @Value("${ranking.social-timeout-millis:3000}") long timeoutMillis,
            @Value("${ranking.social.mock:false}") boolean mock, SeasonService seasons, PlayerEloRepository players) {
        Duration timeout = Duration.ofMillis(timeoutMillis);
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        social = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        this.mock = mock;
        this.seasons = seasons;
        this.players = players;
    }

    /** SPEC §6.1: GET /internal/friends/{accountId} returns a raw JSON array of account ids. */
    @Override
    public List<UUID> friends(UUID accountId) {
        if (mock) return seasonTop(accountId);
        try {
            UUID[] friends = social.get().uri("/internal/friends/{accountId}", accountId).retrieve().body(UUID[].class);
            if (friends == null) throw RankingExceptions.socialUnavailable();
            return Arrays.asList(friends);
        } catch (RestClientException ex) {
            throw RankingExceptions.socialUnavailable();
        }
    }

    private List<UUID> seasonTop(UUID accountId) {
        return players.findBySeasonIdAndIsDeletedFalse(seasons.active().getId(),
                        PageRequest.of(0, RankingConstants.MAX_FRIENDS, RankingConstants.LEADERBOARD_SORT))
                .getContent().stream().map(PlayerEloEntity::getAccountId)
                .filter(friend -> !friend.equals(accountId)).toList();
    }
}
