package com.msspoker.gameservice.config;

import com.msspoker.gameservice.service.MatchPersistenceService;
import com.msspoker.gameservice.service.SettingsService;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GameStartup {
    private final SettingsService settings;
    private final MatchPersistenceService matches;

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        settings.list();
        matches.cancelInterruptedMatches();
    }
}
