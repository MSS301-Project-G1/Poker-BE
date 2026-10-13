package com.msspoker.rankingservice.service.impl;

import com.msspoker.rankingservice.enums.RewardType;
import com.msspoker.rankingservice.mapper.SeasonMapper;
import com.msspoker.rankingservice.repository.RewardGrantRepository;
import com.msspoker.rankingservice.service.GrantDispatcher;
import com.msspoker.rankingservice.service.ShopClient;
import com.msspoker.rankingservice.service.WalletClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ranking.rewards.enabled", havingValue = "true", matchIfMissing = true)
public class GrantDispatcherImpl implements GrantDispatcher {
    private final RewardGrantRepository repository;
    private final WalletClient wallet;
    private final ShopClient shop;
    private final SeasonMapper mapper;
    private final ReentrantLock dispatching = new ReentrantLock();

    @Scheduled(fixedDelayString = "${ranking.rewards.poll-millis:5000}")
    @Override
    public void dispatchPending() {
        if (!dispatching.tryLock()) return;
        try {
            for (var grant : repository.findTop20ByGrantedAtIsNullOrderByCreatedAtAscIdAsc()) {
                grant.setAttempts(grant.getAttempts() + 1);
                try {
                    if (grant.getRewardType() == RewardType.COIN) wallet.credit(mapper.credit(grant));
                    else shop.grant(mapper.item(grant));
                    grant.setGrantedAt(Instant.now());
                    repository.save(grant);
                } catch (Exception ex) {
                    repository.save(grant);
                    log.warn("Reward grant {} not accepted yet; will retry", grant.getIdempotencyKey(), ex);
                    break;
                }
            }
        } finally {
            dispatching.unlock();
        }
    }
}
