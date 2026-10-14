package com.msspoker.competitionservice.dto.request;

public record TableSettingsRequest(long smallBlind, long bigBlind, long startingChips, int turnTimeSeconds) {
}
