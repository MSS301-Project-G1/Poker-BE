package com.msspoker.gameservice.validator;

import com.msspoker.gameservice.dto.request.CreateTableRequest;
import com.msspoker.gameservice.exception.GameExceptions;
import com.msspoker.gameservice.service.TableRegistry;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlayerAvailabilityValidator implements CreateTableValidator {
    private final TableRegistry registry;

    @Override
    public void validate(CreateTableRequest request) {
        if (request.playerIds().stream().anyMatch(id -> registry.activeTable(id).isPresent())) {
            throw GameExceptions.playerBusy();
        }
    }
}
