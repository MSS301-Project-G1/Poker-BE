package com.msspoker.authservice.service;

import com.msspoker.authservice.entity.Account;

public interface FirstLoginBonusService {
    /** Saves a pending bonus for a newly activated player, inside the caller's transaction. */
    void queue(Account account);
    void deliverPendingBonuses();
}
