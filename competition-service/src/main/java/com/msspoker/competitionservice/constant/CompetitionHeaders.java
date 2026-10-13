package com.msspoker.competitionservice.constant;

public final class CompetitionHeaders {
    public static final String USER_ID = "X-User-Id";
    public static final String USER_ROLE = "X-User-Role";
    public static final String REQUEST_ID = "X-Request-Id";
    /** Required by game-service outside its dev profile. */
    public static final String GAME_SERVICE_KEY = "X-Game-Service-Key";

    private CompetitionHeaders() {
    }
}
