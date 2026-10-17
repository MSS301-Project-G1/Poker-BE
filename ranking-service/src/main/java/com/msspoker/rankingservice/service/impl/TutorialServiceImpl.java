package com.msspoker.rankingservice.service.impl;

import com.msspoker.rankingservice.config.RankingProperties;
import com.msspoker.rankingservice.constant.RankingConstants;
import com.msspoker.rankingservice.dto.response.TutorialResponse;
import com.msspoker.rankingservice.enums.RewardType;
import com.msspoker.rankingservice.mapper.SeasonMapper;
import com.msspoker.rankingservice.mapper.TutorialMapper;
import com.msspoker.rankingservice.repository.RewardGrantRepository;
import com.msspoker.rankingservice.repository.TutorialCompletionRepository;
import com.msspoker.rankingservice.service.TutorialService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TutorialServiceImpl implements TutorialService {
    private final TutorialCompletionRepository completions;
    private final RewardGrantRepository grants;
    private final TutorialMapper mapper;
    private final SeasonMapper grantMapper;
    private final RankingProperties properties;

    @Transactional(readOnly = true)
    @Override
    public TutorialResponse status(UUID accountId) {
        return completions.findById(accountId).map(mapper::completed)
                .orElseGet(() -> new TutorialResponse(false, null, properties.getTutorialRewardCoins()));
    }

    /**
     * The completion and its coin grant commit together; the grant dispatcher delivers the coins with
     * key tutorial:{accountId}. The tutorial has no related match or season, so refId stays empty.
     */
    @Transactional
    @Override
    public TutorialResponse complete(UUID accountId) {
        long coins = properties.getTutorialRewardCoins();
        Instant now = Instant.now();
        if (completions.insertIfAbsent(accountId, coins, now) == 1 && coins > 0) {
            grants.save(grantMapper.newGrant(accountId, RewardType.COIN, coins, null,
                    RankingConstants.TUTORIAL_REWARD_REASON, null,
                    String.format(RankingConstants.TUTORIAL_KEY_FORMAT, accountId), now));
        }
        return mapper.completed(completions.findById(accountId).orElseThrow());
    }
}
