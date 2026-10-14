package com.msspoker.competitionservice.validator;

import com.msspoker.competitionservice.config.CompetitionProperties;
import com.msspoker.competitionservice.entity.TournamentEntity;
import com.msspoker.competitionservice.entity.TournamentEntryEntity;
import com.msspoker.competitionservice.exception.CompetitionExceptions;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * SPEC §7.7: invitations sent before the confirmation deadline (startAt minus confirm-deadline-minutes)
 * must be confirmed by then; replacements sent after it may be confirmed until the start.
 */
@Component
@RequiredArgsConstructor
public class EliteInviteValidator {
    private final CompetitionProperties properties;

    public Instant confirmBy(TournamentEntity tournament, Instant invitedAt) {
        Instant deadline = tournament.getStartAt().minus(properties.getTournament().getConfirmDeadlineMinutes(),
                ChronoUnit.MINUTES);
        return invitedAt.isBefore(deadline) ? deadline : tournament.getStartAt();
    }

    public boolean expired(TournamentEntity tournament, TournamentEntryEntity entry, Instant now) {
        return !now.isBefore(confirmBy(tournament, entry.getRegisteredAt()));
    }

    public void validateConfirm(TournamentEntity tournament, TournamentEntryEntity entry) {
        if (expired(tournament, entry, Instant.now())) throw CompetitionExceptions.inviteExpired();
    }

    /** ELITE invitations go out invite-lead-minutes before the start, so the start must leave that time. */
    public void validateSchedule(Instant startAt, Instant now) {
        int lead = properties.getTournament().getInviteLeadMinutes();
        if (startAt.isBefore(now.plus(lead, ChronoUnit.MINUTES))) throw CompetitionExceptions.eliteStartTooSoon(lead);
    }
}
