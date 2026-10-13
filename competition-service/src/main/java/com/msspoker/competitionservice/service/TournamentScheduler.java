package com.msspoker.competitionservice.service;

public interface TournamentScheduler {
    /** Sends due ELITE invitations and replacements, then starts or cancels tournaments whose start came. */
    void runDue();
}
