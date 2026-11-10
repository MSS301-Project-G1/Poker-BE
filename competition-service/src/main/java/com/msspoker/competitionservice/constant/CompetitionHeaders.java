package com.msspoker.competitionservice.constant;

public final class CompetitionHeaders {
    public static final String USER_ID = "X-User-Id";
    public static final String USER_ROLE = "X-User-Role";
    public static final String REQUEST_ID = "X-Request-Id";
    /** Local-development identity sent by the shared FE http client (dev profile only). */
    public static final String DEV_USER = "X-Dev-User";
    /** Required by game-service outside its dev profile. */
    public static final String GAME_SERVICE_KEY = "X-Game-Service-Key";
    /** Required by social-service on /internal/**; without it social-service answers 403. */
    public static final String SOCIAL_INTERNAL_KEY = "X-Social-Internal-Key";

    private CompetitionHeaders() {
    }
}
