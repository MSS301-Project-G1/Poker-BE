package com.msspoker.gameservice.constant;

public final class PageConstants {
    public static final String FIRST_PAGE = "0";
    public static final String DEFAULT_SIZE = "20";
    public static final int MAX_SIZE = 100;
    /** Most account ids one status lookup accepts: one page of players. */
    public static final int MAX_LOOKUP_IDS = MAX_SIZE;

    private PageConstants() {
    }
}
