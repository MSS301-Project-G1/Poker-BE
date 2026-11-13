package com.msspoker.matchmakingservice.service.impl.job;

import com.msspoker.matchmakingservice.service.NormalProposalService;

import lombok.RequiredArgsConstructor;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NormalProposalExpiryWorker {
    private final NormalProposalService proposals;

    @Scheduled(fixedDelayString = "${matchmaking.finder.fixed-delay-ms:1000}")
    public void expire() {
        proposals.expire();
    }
}
