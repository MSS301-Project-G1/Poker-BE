package com.msspoker.common.event;

import java.util.UUID;

public record AccountRegisteredEvent(UUID accountId) {
    public static final String EVENT_TYPE = "account.registered";
}
