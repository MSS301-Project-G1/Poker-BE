package com.msspoker.rankingservice.service;

import com.msspoker.rankingservice.dto.response.TutorialResponse;

import java.util.UUID;

public interface TutorialService {
    TutorialResponse status(UUID accountId);

    /** Records the completion and queues TUTORIAL_REWARD once; repeating returns the first completion. */
    TutorialResponse complete(UUID accountId);
}
