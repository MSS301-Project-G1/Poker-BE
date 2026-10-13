package com.msspoker.competitionservice.validator;

import com.msspoker.competitionservice.config.CompetitionProperties;
import com.msspoker.competitionservice.dto.request.CreateRoomRequest;
import com.msspoker.competitionservice.exception.CompetitionExceptions;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoomSettingsValidator {
    private final CompetitionProperties properties;

    /** Rejects rooms game-service would refuse: too many seats or a big blind above the starting stack. */
    public void validate(CreateRoomRequest request) {
        CompetitionProperties.Room room = properties.getRoom();
        if (request.maxPlayers() < room.getMinPlayers() || request.maxPlayers() > room.getMaxPlayers()
                || request.bigBlind() > room.getStartingChips()) {
            throw CompetitionExceptions.invalidRoomSettings();
        }
    }
}
