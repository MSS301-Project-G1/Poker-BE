package com.msspoker.competitionservice.service.impl;

import com.msspoker.competitionservice.config.CompetitionProperties;
import com.msspoker.competitionservice.service.PrizeCalculator;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PrizeCalculatorImpl implements PrizeCalculator {
    private final CompetitionProperties properties;

    @Override
    public List<Long> split(long pool, int finalists) {
        List<Integer> shares = properties.getTournament().getPrizeShares();
        List<Integer> paid = shares.subList(0, Math.max(0, Math.min(shares.size(), finalists)));
        long weight = paid.stream().mapToLong(Integer::longValue).sum();
        if (pool <= 0 || weight <= 0) return paid.stream().map(share -> 0L).toList();
        List<Long> prizes = new ArrayList<>();
        for (int share : paid) prizes.add(Math.multiplyExact(pool, (long) share) / weight);
        prizes.set(0, prizes.getFirst() + pool - prizes.stream().mapToLong(Long::longValue).sum());
        return List.copyOf(prizes);
    }
}
