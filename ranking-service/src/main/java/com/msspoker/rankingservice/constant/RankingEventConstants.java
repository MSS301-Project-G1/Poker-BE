package com.msspoker.rankingservice.constant;

import com.msspoker.common.event.AccountRegisteredEvent;

public final class RankingEventConstants {
    public static final String EXCHANGE = "poker.events";
    public static final String ACCOUNT_REGISTERED = AccountRegisteredEvent.EVENT_TYPE;
    public static final String MATCH_FINISHED = "match.finished";
    public static final String NOTIFICATION_REQUESTED = "notification.requested";
    public static final String ACCOUNT_REGISTERED_QUEUE = "ranking-service." + ACCOUNT_REGISTERED;
    public static final String MATCH_FINISHED_QUEUE = "ranking-service." + MATCH_FINISHED;
    public static final int CONFIRM_TIMEOUT_MILLIS = 5000;

    private RankingEventConstants() {
    }
}
