package com.msspoker.competitionservice.constant;

public final class CompetitionEventConstants {
    public static final String EXCHANGE = "poker.events";
    public static final String MATCH_FINISHED = "match.finished";
    public static final String MATCH_FINISHED_QUEUE = "competition-service." + MATCH_FINISHED;
    public static final String NOTIFICATION_REQUESTED = "notification.requested";
    public static final int CONFIRM_TIMEOUT_MILLIS = 5000;

    private CompetitionEventConstants() {
    }
}
