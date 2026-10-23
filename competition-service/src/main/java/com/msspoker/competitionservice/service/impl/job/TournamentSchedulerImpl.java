package com.msspoker.competitionservice.service.impl.job;

import com.msspoker.competitionservice.config.CompetitionProperties;
import com.msspoker.competitionservice.enums.TournamentStatus;
import com.msspoker.competitionservice.enums.TournamentType;
import com.msspoker.competitionservice.repository.TournamentRepository;
import com.msspoker.competitionservice.service.EliteInviteService;
import com.msspoker.competitionservice.service.job.TournamentScheduler;
import com.msspoker.competitionservice.service.TournamentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class TournamentSchedulerImpl implements TournamentScheduler {
    private final TournamentRepository tournaments;
    private final EliteInviteService invites;
    private final TournamentService service;
    private final CompetitionProperties properties;
    private final ReentrantLock running = new ReentrantLock();

    /** Each step is idempotent; a failed tournament is logged and retried on the next run. */
    @Scheduled(fixedDelayString = "${competition.tournament.schedule-millis:30000}")
    @Override
    public void runDue() {
        if (!running.tryLock()) return;
        try {
            Instant now = Instant.now();
            CompetitionProperties.Tournament config = properties.getTournament();
            each("invite", tournaments.findDueForInvites(TournamentType.ELITE, TournamentStatus.SCHEDULED,
                    now.plus(config.getInviteLeadMinutes(), ChronoUnit.MINUTES)), invites::invite);
            each("replace", tournaments.findDueForReplacements(TournamentType.ELITE, TournamentStatus.SCHEDULED,
                    now.plus(config.getConfirmDeadlineMinutes(), ChronoUnit.MINUTES)), invites::replace);
            each("start", tournaments.findDueToStart(TournamentStatus.SCHEDULED, now), service::startDue);
        } finally {
            running.unlock();
        }
    }

    private void each(String step, List<UUID> tournamentIds, Consumer<UUID> action) {
        for (UUID tournamentId : tournamentIds) {
            try {
                action.accept(tournamentId);
            } catch (Exception ex) {
                log.warn("Tournament {} {} step failed; will retry", tournamentId, step, ex);
            }
        }
    }
}
