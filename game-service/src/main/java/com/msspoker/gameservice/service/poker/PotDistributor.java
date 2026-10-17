package com.msspoker.gameservice.service.poker;

import com.msspoker.gameservice.model.poker.HandRank;
import com.msspoker.gameservice.model.poker.PotContribution;
import com.msspoker.gameservice.model.poker.PotDistribution;

import java.util.List;
import java.util.Map;

public interface PotDistributor {
    PotDistribution distribute(
            List<PotContribution> contributions, Map<Integer, HandRank> hands, int dealerSeat, int seatCount);
}
