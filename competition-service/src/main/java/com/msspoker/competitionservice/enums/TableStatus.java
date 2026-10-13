package com.msspoker.competitionservice.enums;

/** PENDING tables wait for game-service CreateTable, which is retried until it succeeds. */
public enum TableStatus {
    PENDING, PLAYING, FINISHED
}
