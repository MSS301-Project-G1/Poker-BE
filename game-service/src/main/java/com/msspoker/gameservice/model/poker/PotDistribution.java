package com.msspoker.gameservice.model.poker;

import java.util.List;
import java.util.Map;

public record PotDistribution(Map<Integer, Long> winnings, Map<Integer, Long> refunds, List<PotAward> pots) {
    public PotDistribution {
        winnings = Map.copyOf(winnings);
        refunds = Map.copyOf(refunds);
        pots = List.copyOf(pots);
    }
}
