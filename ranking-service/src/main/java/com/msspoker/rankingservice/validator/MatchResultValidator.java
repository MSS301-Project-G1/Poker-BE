package com.msspoker.rankingservice.validator;

import com.msspoker.rankingservice.dto.event.MatchFinishedEvent;
import com.msspoker.rankingservice.dto.event.PlacementEvent;
import com.msspoker.rankingservice.exception.RankingExceptions;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Component
public class MatchResultValidator {
    /** Places must be unique and cover 1..n; each account appears once. */
    public void validate(MatchFinishedEvent event) {
        if (event == null || event.matchId() == null || event.mode() == null || event.entryFee() < 0
                || event.placements() == null || event.placements().isEmpty()) {
            throw RankingExceptions.invalidMatchResult();
        }
        int players = event.placements().size();
        Set<UUID> accounts = new HashSet<>();
        Set<Integer> places = new HashSet<>();
        for (PlacementEvent placement : event.placements()) {
            if (placement == null || placement.accountId() == null || placement.place() < 1
                    || placement.place() > players || !accounts.add(placement.accountId())
                    || !places.add(placement.place())) {
                throw RankingExceptions.invalidMatchResult();
            }
        }
    }
}
