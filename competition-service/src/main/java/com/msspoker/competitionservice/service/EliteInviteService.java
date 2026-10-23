package com.msspoker.competitionservice.service;

import java.util.UUID;

/** SPEC §7.7 ELITE invitations; both steps are idempotent and run from the tournament scheduler. */
public interface EliteInviteService {
    /** Invites the top `slots` players of the leaderboard and sends TOURNAMENT_INVITE. */
    void invite(UUID tournamentId);

    /** Expires invitations unconfirmed at the deadline and invites the next ranks into the open places. */
    void replace(UUID tournamentId);
}
