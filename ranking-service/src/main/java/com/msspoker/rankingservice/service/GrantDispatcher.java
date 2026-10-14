package com.msspoker.rankingservice.service;

public interface GrantDispatcher {
    /** Sends pending season reward coins to wallet-service and items to shop-service. */
    void dispatchPending();
}
