package com.msspoker.competitionservice.service.job;

public interface TournamentTableLauncher {
    /** Opens PENDING tournament tables through game-service; failures stay PENDING for the next attempt. */
    void launchPending();
}
