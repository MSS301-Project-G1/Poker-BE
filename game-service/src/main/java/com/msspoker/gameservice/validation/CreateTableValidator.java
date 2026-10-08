package com.msspoker.gameservice.validation;

import com.msspoker.gameservice.api.GameDtos.CreateTable;

public interface CreateTableValidator {
    void validate(CreateTable request);
}
