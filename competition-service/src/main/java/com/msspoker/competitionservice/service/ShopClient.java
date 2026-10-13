package com.msspoker.competitionservice.service;

import com.msspoker.competitionservice.dto.request.ShopGrantRequest;

public interface ShopClient {
    /** Throws when the shop did not accept the grant; the idempotency key makes retries safe. */
    void grant(ShopGrantRequest request);
}
