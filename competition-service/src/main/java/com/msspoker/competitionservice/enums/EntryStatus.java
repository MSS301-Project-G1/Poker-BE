package com.msspoker.competitionservice.enums;

/**
 * INVITED/CONFIRMED belong to ELITE tournaments, REGISTERED to OPEN ones (SPEC §9.4). EXPIRED marks
 * ELITE invitations that were not confirmed in time; they keep their row so nobody is invited twice.
 */
public enum EntryStatus {
    INVITED, CONFIRMED, REGISTERED, ELIMINATED, EXPIRED
}
