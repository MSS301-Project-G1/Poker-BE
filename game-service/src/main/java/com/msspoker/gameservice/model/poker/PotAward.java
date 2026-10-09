package com.msspoker.gameservice.model.poker;

import java.util.List;

public record PotAward(long amount, List<Integer> eligibleSeats, List<Integer> winnerSeats) {
    public PotAward {
        eligibleSeats = List.copyOf(eligibleSeats);
        winnerSeats = List.copyOf(winnerSeats);
    }
}
