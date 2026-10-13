package com.msspoker.competitionservice.service;

import java.util.List;

public interface PrizeCalculator {
    /**
     * Splits the pool over the first min(shares, finalists) places by the configured shares, scaled
     * to the places that exist; the rounding remainder goes to the champion, so the whole pool is paid.
     */
    List<Long> split(long pool, int finalists);
}
