package com.msspoker.competitionservice.service.job;

public interface TournamentScheduler {
    /** Sends due ELITE invitations and replacements, then starts or cancels tournaments whose start came. */
    void runDue();
}
