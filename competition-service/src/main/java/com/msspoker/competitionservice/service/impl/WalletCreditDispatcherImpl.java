package com.msspoker.competitionservice.service.impl;

import com.msspoker.competitionservice.mapper.TournamentMapper;
import com.msspoker.competitionservice.repository.WalletCreditRepository;
import com.msspoker.competitionservice.service.WalletClient;
import com.msspoker.competitionservice.service.WalletCreditDispatcher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
@RequiredArgsConstructor
public class WalletCreditDispatcherImpl implements WalletCreditDispatcher {
    private final WalletCreditRepository repository;
    private final WalletClient wallet;
    private final TournamentMapper mapper;
    private final ReentrantLock dispatching = new ReentrantLock();

    @Scheduled(fixedDelayString = "${competition.wallet.poll-millis:5000}")
    @Override
    public void dispatchPending() {
        if (!dispatching.tryLock()) return;
        try {
            for (var credit : repository.findTop20ByCreditedAtIsNullOrderByCreatedAtAscIdAsc()) {
                credit.setAttempts(credit.getAttempts() + 1);
                try {
                    wallet.credit(mapper.transaction(credit));
                    credit.setCreditedAt(Instant.now());
                    repository.save(credit);
                } catch (Exception ex) {
                    repository.save(credit);
                    log.warn("Wallet credit {} not accepted yet; will retry", credit.getIdempotencyKey(), ex);
                    break;
                }
            }
        } finally {
            dispatching.unlock();
        }
    }
}
