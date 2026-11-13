package com.msspoker.matchmakingservice.constant;

public final class MatchmakingConstants {
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String REDIS_PREFIX = "mm:v1:";
    public static final int PARTY_PASSWORD_DIGITS = 6;
    public static final int PRESENCE_SWEEP_BATCH = 100;

    private MatchmakingConstants() {}
}
