package com.msspoker.rankingservice.service.client;

import com.msspoker.rankingservice.dto.request.ShopGrantRequest;

public interface ShopClient {
    /** Throws when the shop did not accept the grant; the idempotency key makes retries safe. */
    void grant(ShopGrantRequest request);
}
