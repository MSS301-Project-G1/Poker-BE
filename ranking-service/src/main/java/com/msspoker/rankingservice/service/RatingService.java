package com.msspoker.rankingservice.service;

import com.msspoker.common.event.AccountRegisteredEvent;
import com.msspoker.rankingservice.dto.event.MatchFinishedEvent;

public interface RatingService {
    void accountRegistered(AccountRegisteredEvent event);

    void matchFinished(MatchFinishedEvent event);
}
