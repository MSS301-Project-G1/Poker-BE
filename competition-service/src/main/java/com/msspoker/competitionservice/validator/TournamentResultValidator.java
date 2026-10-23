package com.msspoker.competitionservice.validator;

import com.msspoker.competitionservice.dto.event.MatchFinishedEvent;
import com.msspoker.competitionservice.dto.event.PlacementEvent;
import com.msspoker.competitionservice.entity.TournamentSeatEntity;
import com.msspoker.competitionservice.exception.CompetitionExceptions;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class TournamentResultValidator {
    /** Placements must cover exactly the table's seats with unique places 1..n. */
    public void validate(MatchFinishedEvent event, List<TournamentSeatEntity> seats) {
        List<PlacementEvent> placements = event.placements();
        if (placements == null || placements.size() != seats.size()) throw CompetitionExceptions.tableResultMismatch();
        Set<UUID> seated = seats.stream().map(TournamentSeatEntity::getAccountId).collect(Collectors.toSet());
        Set<UUID> placed = new HashSet<>();
        Set<Integer> places = new HashSet<>();
        for (PlacementEvent placement : placements) {
            if (placement == null || !seated.contains(placement.accountId()) || !placed.add(placement.accountId())
                    || placement.place() < 1 || placement.place() > seats.size() || !places.add(placement.place())) {
                throw CompetitionExceptions.tableResultMismatch();
            }
        }
    }
}
