package com.msspoker.rankingservice.service.impl.job;

import com.msspoker.rankingservice.mapper.RankingMapper;
import com.msspoker.rankingservice.repository.RewardCreditRepository;
import com.msspoker.rankingservice.service.job.RewardDispatcher;
import com.msspoker.rankingservice.service.client.WalletClient;

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
public class RewardDispatcherImpl implements RewardDispatcher {
    private final RewardCreditRepository repository;
    private final WalletClient wallet;
    private final RankingMapper mapper;
    private final ReentrantLock dispatching = new ReentrantLock();

    @Scheduled(fixedDelayString = "${ranking.rewards.poll-millis:5000}")
    @Override
    public void dispatchPending() {
        if (!dispatching.tryLock()) return;
        try {
            for (var reward : repository.findTop20ByCreditedAtIsNullOrderByCreatedAtAscIdAsc()) {
                reward.setAttempts(reward.getAttempts() + 1);
                try {
                    wallet.credit(mapper.credit(reward));
                    reward.setCreditedAt(Instant.now());
                    repository.save(reward);
                } catch (Exception ex) {
                    repository.save(reward);
                    log.warn("Rank reward {} not credited yet; will retry", reward.getIdempotencyKey(), ex);
                    break;
                }
            }
        } finally {
            dispatching.unlock();
        }
    }
}
