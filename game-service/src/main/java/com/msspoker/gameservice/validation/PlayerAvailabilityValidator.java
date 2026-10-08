package com.msspoker.gameservice.validation;

import com.msspoker.gameservice.api.GameDtos.CreateTable;
import com.msspoker.gameservice.exception.GameExceptions;
import com.msspoker.gameservice.service.TableRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlayerAvailabilityValidator implements CreateTableValidator {
    private final TableRegistry registry;

    @Override
    public void validate(CreateTable request) {
        if (request.playerIds().stream().anyMatch(id -> registry.activeTable(id).isPresent())) {
            throw GameExceptions.playerBusy();
        }
    }
}
