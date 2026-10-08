package com.msspoker.gameservice.service.poker;

import com.msspoker.gameservice.constant.PokerConstants;
import com.msspoker.gameservice.exception.GameExceptions;
import com.msspoker.gameservice.model.poker.HandRank;
import com.msspoker.gameservice.model.poker.PotAward;
import com.msspoker.gameservice.model.poker.PotContribution;
import com.msspoker.gameservice.model.poker.PotDistribution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public final class PotDistributor {
    private PotDistributor() {
    }

    public static PotDistribution distribute(
            List<PotContribution> contributions, Map<Integer, HandRank> hands, int dealerSeat, int seatCount) {
        if (contributions == null || hands == null || contributions.isEmpty()) {
            throw GameExceptions.invalidPotInput();
        }
        if (seatCount < PokerConstants.MIN_TABLE_SEATS || dealerSeat < 0 || dealerSeat >= seatCount) {
            throw GameExceptions.invalidTable();
        }

        HashSet<Integer> occupiedSeats = new HashSet<>();
        List<Integer> activeSeats = new ArrayList<>();
        long committed = 0;
        for (PotContribution contribution : contributions) {
            if (contribution == null || contribution.seat() >= seatCount
                    || !occupiedSeats.add(contribution.seat())) {
                throw GameExceptions.invalidContribution();
            }
            committed = Math.addExact(committed, contribution.chips());
            if (!contribution.folded()) {
                activeSeats.add(contribution.seat());
            }
        }
        if (committed == 0 || activeSeats.isEmpty()) {
            throw GameExceptions.invalidPot();
        }

        List<Long> levels = contributions.stream().map(PotContribution::chips)
                .filter(chips -> chips > 0).distinct().sorted().toList();
        Map<Integer, Long> winnings = new HashMap<>();
        Map<Integer, Long> refunds = new HashMap<>();
        List<PotAward> pots = new ArrayList<>();
        long previousLevel = 0;

        for (long level : levels) {
            List<PotContribution> contributors = contributions.stream()
                    .filter(contribution -> contribution.chips() >= level).toList();
            long amount = Math.multiplyExact(level - previousLevel, contributors.size());
            previousLevel = level;

            if (contributors.size() == 1) {
                refunds.merge(contributors.getFirst().seat(), amount, Math::addExact);
                continue;
            }

            List<Integer> eligible = contributors.stream().filter(contribution -> !contribution.folded())
                    .map(PotContribution::seat).toList();
            if (eligible.isEmpty() && activeSeats.size() == 1) {
                eligible = List.of(activeSeats.getFirst());
            }
            if (eligible.isEmpty()) {
                throw GameExceptions.noEligiblePlayer();
            }

            List<Integer> winners = winners(eligible, hands);
            winners.sort(Comparator.comparingInt(seat ->
                    Math.floorMod(seat - dealerSeat - 1, seatCount)));
            long share = amount / winners.size();
            int extraChips = (int) (amount % winners.size());
            for (int i = 0; i < winners.size(); i++) {
                winnings.merge(winners.get(i), share + (i < extraChips ? 1 : 0), Math::addExact);
            }
            pots.add(new PotAward(amount, eligible, winners));
        }

        long distributed = 0;
        for (long value : winnings.values()) {
            distributed = Math.addExact(distributed, value);
        }
        for (long value : refunds.values()) {
            distributed = Math.addExact(distributed, value);
        }
        if (distributed != committed) {
            throw GameExceptions.chipConservationFailed();
        }
        return new PotDistribution(winnings, refunds, pots);
    }

    private static List<Integer> winners(List<Integer> eligible, Map<Integer, HandRank> hands) {
        if (eligible.size() == 1) {
            return new ArrayList<>(eligible);
        }
        HandRank best = null;
        List<Integer> winners = new ArrayList<>();
        for (int seat : eligible) {
            HandRank hand = hands.get(seat);
            if (hand == null) {
                throw GameExceptions.missingShowdownHand(seat);
            }
            int comparison = best == null ? 1 : hand.compareTo(best);
            if (comparison > 0) {
                best = hand;
                winners.clear();
                winners.add(seat);
            } else if (comparison == 0) {
                winners.add(seat);
            }
        }
        return winners;
    }
}
