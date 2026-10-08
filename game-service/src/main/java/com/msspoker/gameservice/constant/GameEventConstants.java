package com.msspoker.gameservice.constant;

public final class GameEventConstants {
    public static final String EXCHANGE = "poker.events";
    public static final String STARTED = "match.started";
    public static final String FINISHED = "match.finished";
    public static final int CONFIRM_TIMEOUT_MILLIS = 5000;

    private GameEventConstants() {
    }
}
