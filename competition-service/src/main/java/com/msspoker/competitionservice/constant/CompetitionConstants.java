package com.msspoker.competitionservice.constant;

public final class CompetitionConstants {
    /** Room codes avoid 0/O and 1/I so players can read them aloud. */
    public static final String ROOM_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    public static final int MAX_PAGE_SIZE = 100;
    public static final int MIN_PASSWORD_LENGTH = 4;
    public static final int MAX_PASSWORD_LENGTH = 32;
    public static final int MAX_TABLE_SEATS = 10;
    public static final int MAX_NAME_LENGTH = 100;
    public static final String ADMIN_ROLE = "ADMIN";
    public static final String GAME_SOURCE_CONFLICT = "GAME_SOURCE_CONFLICT";
    public static final String GAME_PLAYER_BUSY = "GAME_PLAYER_BUSY";
    public static final String GAME_FORBIDDEN = "GAME_FORBIDDEN";
    public static final String WALLET_INSUFFICIENT_BALANCE = "WALLET_INSUFFICIENT_BALANCE";
    public static final String TOURNAMENT_ENTRY_FEE = "TOURNAMENT_ENTRY_FEE";
    public static final String TOURNAMENT_REFUND = "TOURNAMENT_REFUND";
    public static final String TOURNAMENT_PRIZE = "TOURNAMENT_PRIZE";
    /** SPEC §5.4 key format: reason:sourceId:accountId. */
    public static final String TOURNAMENT_FEE_KEY_FORMAT = "tournament-fee:%s:%s";
    public static final String TOURNAMENT_REFUND_KEY_FORMAT = "tournament-refund:%s:%s";
    public static final String TOURNAMENT_PRIZE_KEY_FORMAT = "tournament-prize:%s:%s";
    public static final String TOURNAMENT_ITEM_KEY_FORMAT = "tournament-item:%s:%s";
    /** Largest limit ranking-service accepts on /internal/ranking/top. */
    public static final int MAX_RANKING_LIMIT = 100;

    private CompetitionConstants() {
    }
}
