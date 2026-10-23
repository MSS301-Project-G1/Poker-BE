package com.msspoker.competitionservice.service.impl.job;

import com.msspoker.competitionservice.mapper.TournamentMapper;
import com.msspoker.competitionservice.repository.ItemGrantRepository;
import com.msspoker.competitionservice.service.job.ItemGrantDispatcher;
import com.msspoker.competitionservice.service.client.ShopClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
@RequiredArgsConstructor
public class ItemGrantDispatcherImpl implements ItemGrantDispatcher {
    private final ItemGrantRepository repository;
    private final ShopClient shop;
    private final TournamentMapper mapper;
    private final ReentrantLock dispatching = new ReentrantLock();

    @Scheduled(fixedDelayString = "${competition.shop.poll-millis:5000}")
    @Override
    public void dispatchPending() {
        if (!dispatching.tryLock()) return;
        try {
            for (var grant : repository.findTop20ByGrantedAtIsNullOrderByCreatedAtAscIdAsc()) {
                grant.setAttempts(grant.getAttempts() + 1);
                try {
                    shop.grant(mapper.shopGrant(grant));
                    grant.setGrantedAt(Instant.now());
                    repository.save(grant);
                } catch (Exception ex) {
                    repository.save(grant);
                    log.warn("Item grant {} not accepted yet; will retry", grant.getIdempotencyKey(), ex);
                    break;
                }
            }
        } finally {
            dispatching.unlock();
        }
    }
}
