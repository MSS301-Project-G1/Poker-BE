package com.msspoker.matchmakingservice.service.client.stub;

import com.msspoker.matchmakingservice.dto.response.PlayerRankingResponse;
import com.msspoker.matchmakingservice.service.client.RankingClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "matchmaking.ranking.stub-enabled", havingValue = "true")
public class StubRankingClient implements RankingClient {
    @Override
    public PlayerRankingResponse ranking(UUID accountId) {
        return new PlayerRankingResponse(accountId, 0, 0, 0);
    }
}
