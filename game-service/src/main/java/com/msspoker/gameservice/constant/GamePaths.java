package com.msspoker.gameservice.constant;

public final class GamePaths {
    public static final String HEALTH = "/actuator/health";
    public static final String PUBLIC_API = "/api/";
    public static final String ADMIN_API = "/api/admin/";
    public static final String ACCOUNT_RESTRICTIONS = "/internal/accounts/{accountId}/restrictions";

    private GamePaths() {
    }
}
