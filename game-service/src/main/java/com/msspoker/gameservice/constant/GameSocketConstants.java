package com.msspoker.gameservice.constant;

public final class GameSocketConstants {
    public static final String ENDPOINT = "/ws/game";
    public static final String APP_PREFIX = "/app";
    public static final String BROKER_PREFIX = "/queue";
    public static final String USER_PREFIX = "/user";
    public static final String TABLE_QUEUE = "/queue/tables/";
    public static final String TAUNT_QUEUE_SUFFIX = "/taunts";
    public static final String ERROR_QUEUE = "/queue/game-errors";
    public static final String USER_ERROR_QUEUE = USER_PREFIX + ERROR_QUEUE;
    public static final int MESSAGE_SIZE_LIMIT_BYTES = 8192;
    public static final int SEND_BUFFER_SIZE_LIMIT_BYTES = 65536;
    public static final int SEND_TIME_LIMIT_MILLIS = 10000;
    public static final String CHANNEL_THREAD_PREFIX = "game-ws-";

    private GameSocketConstants() {
    }
}
